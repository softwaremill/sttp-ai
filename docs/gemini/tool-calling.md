# Tool calling

## Custom Tools

Define your own tools with `Tool.Function`. `parameters` is a raw JSON Schema (`io.circe.Json`), passed to the API byte-for-byte — handy when the schema comes from an MCP server or another source you don't want re-encoded. See [JSON Schemas: structured outputs & tools](../other/json-schemas.md) for ways to produce a schema.

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::gemini:@VERSION@

import io.circe.Json
import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.models.Tool
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.models.InteractionInput

object ToolCallingExample:
  def main(args: Array[String]): Unit =
    val gemini = GeminiSyncClient.fromEnv
    try {
      val weatherTool = Tool.Function(
        name = "get_weather",
        description = Some("Get current weather for a location"),
        parameters = Json.obj(
          "type" -> Json.fromString("object"),
          "properties" -> Json.obj(
            "location" -> Json.obj(
              "type" -> Json.fromString("string"),
              "description" -> Json.fromString("City name")
            ),
            "unit" -> Json.obj(
              "type" -> Json.fromString("string"),
              "enum" -> Json.arr(Json.fromString("celsius"), Json.fromString("fahrenheit"))
            )
          ),
          "required" -> Json.arr(Json.fromString("location"))
        )
      )

      val request = InteractionRequest.withTools(
        model = "gemini-2.5-flash",
        input = InteractionInput.TextInput("What's the weather in Paris?"),
        tools = List(weatherTool)
      )

      val response = gemini.createInteraction(request)

      // Inspect any function calls the model requested
      response.functionCalls.foreach(fc => println(s"${fc.name}(${fc.arguments.noSpaces})"))
    } finally gemini.close()
```

## Answering function calls

`response.functionCalls` gives you every `Step.FunctionCall` the model asked for (`id`, `name`, `arguments` as `io.circe.Json`). Execute them yourself, then continue the conversation by replaying the full step history — original prompt, the model's function call, and your `Step.FunctionResult` — via `InteractionInput.StepsInput` with `store = Some(false)`:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::gemini:@VERSION@

import io.circe.Json
import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.models.{InteractionInput, Step, Tool}
import sttp.ai.gemini.requests.InteractionRequest

object ToolReplyExample:
  def main(args: Array[String]): Unit =
    val gemini = GeminiSyncClient.fromEnv
    try {
      val weatherTool = Tool.Function(
        name = "get_weather",
        description = Some("Get current weather for a location"),
        parameters = Json.obj(
          "type" -> Json.fromString("object"),
          "properties" -> Json.obj("location" -> Json.obj("type" -> Json.fromString("string"))),
          "required" -> Json.arr(Json.fromString("location"))
        )
      )

      val first = gemini.createInteraction(
        InteractionRequest
          .withTools("gemini-2.5-flash", InteractionInput.TextInput("What's the weather in Paris?"), List(weatherTool))
          .copy(store = Some(false))
      )

      first.functionCalls.headOption.foreach { call =>
        // ... execute the tool yourself, e.g. look up the weather for `call.arguments`
        val toolResult = Json.obj("temperature" -> Json.fromString("22C"), "conditions" -> Json.fromString("sunny"))

        val steps = List(
          Step.userText("What's the weather in Paris?"),
          call,
          Step.FunctionResult(callId = call.id, name = call.name, result = toolResult)
        )

        val second = gemini.createInteraction(
          InteractionRequest(
            model = "gemini-2.5-flash",
            input = InteractionInput.StepsInput(steps),
            tools = Some(List(weatherTool)),
            store = Some(false)
          )
        )
        println(second.outputText)
      }
    } finally gemini.close()
```

## Built-in tools

Two Google-hosted tools are available alongside custom `Tool.Function`s — no `parameters` needed, since Google executes them server-side:

- **`Tool.GoogleSearch`** — grounds the response in web search results
- **`Tool.CodeExecution`** — lets the model run code in a sandboxed environment

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::gemini:@VERSION@

import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.models.{InteractionInput, Tool}
import sttp.ai.gemini.requests.InteractionRequest

object BuiltinToolsExample:
  def main(args: Array[String]): Unit =
    val gemini = GeminiSyncClient.fromEnv
    try {
      val request = InteractionRequest.withTools(
        model = "gemini-2.5-flash",
        input = InteractionInput.TextInput("What was the most recent SpaceX launch?"),
        tools = List(Tool.GoogleSearch)
      )
      val response = gemini.createInteraction(request)
      println(response.outputText)
    } finally gemini.close()
```

Custom and built-in tools can be mixed in the same `tools` list.

## Using the agent loop

For a full tool-calling loop — the model calls a tool, your code runs it, the result is fed back, repeat until the model produces a final answer — use `GeminiAgent` instead of driving `createInteraction` by hand. It plugs into the same `sttp.ai.core.agent` framework used by the OpenAI and Claude agents (see [agents/quickstart.md](../agents/quickstart.md)); internally its backend always uses stateless `InteractionInput.StepsInput` replay with `store = Some(false)`, so no server-side state is required between iterations.

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::gemini:@VERSION@

import sttp.ai.core.agent.*
import sttp.ai.gemini.agent.GeminiAgent
import sttp.ai.gemini.config.GeminiConfig
import sttp.client4.DefaultSyncBackend
import sttp.tapir.Schema

object AgentExample:
  case class WeatherInput(location: String) derives io.circe.Codec.AsObject, Schema

  def main(args: Array[String]): Unit =
    val weatherTool = AgentTool.fromFunction(
      "get_weather",
      "Get the current weather for a location"
    ) { (input: WeatherInput) =>
      s"The weather in ${input.location} is 22C, sunny"
    }

    val backend = DefaultSyncBackend()
    try {
      val agent = GeminiAgent
        .synchronous(GeminiConfig.fromEnv, "gemini-2.5-flash")
        .maxIterations(5)
        .tools(weatherTool)
        .build

      val result = agent.run("What's the weather in Paris?")(backend)

      println(s"Answer: ${result.finalAnswer}")
      println(s"Iterations: ${result.iterations}")
    } finally backend.close()
```

**For effect systems:** use `GeminiAgent.builder[F]` (e.g. `builder[IO]`) instead of `synchronous`, then add configuration and `.build`, exactly as with `OpenAIAgent`/`ClaudeAgent`.
