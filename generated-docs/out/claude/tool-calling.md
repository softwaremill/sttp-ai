# Tool calling

Tools let Claude request that your application execute a function and report its result back. Each custom tool is described by a name, a description, and an input schema. See [JSON Schemas: structured outputs & tools](../other/json-schemas.md) for schema background — Claude's custom tools use their own small schema model (`ToolInputSchema`/`PropertySchema`), or accept raw JSON Schema via `Tool.CustomRaw` for schemas those types can't express.

## Custom tools

Define your own tools that Claude calls and your application executes:

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.9.0

import sttp.ai.claude.models.{ClaudeModel, ContentBlock, Message, PropertySchema, Tool, ToolInputSchema}
import sttp.ai.claude.requests.MessageRequest

val weatherTool = Tool(
  name = "get_weather",
  description = "Get current weather for a location",
  inputSchema = ToolInputSchema(
    `type` = "object",
    properties = Map(
      "location" -> PropertySchema(`type` = "string", description = Some("City name")),
      "unit" -> PropertySchema(`type` = "string", `enum` = Some(List("celsius", "fahrenheit")))
    ),
    required = Some(List("location"))
  )
)

val request = MessageRequest.withTools(
  model = ClaudeModel.ClaudeSonnet5.value,
  messages = List(Message.user(List(ContentBlock.text("What's the weather in Paris?")))),
  maxTokens = 1000,
  tools = List(weatherTool)
)
```

## Answering tool calls

When Claude decides to call a tool, the response contains `ContentBlock.ToolUse` blocks. Execute the tool yourself, then send the result back as a `Message.toolResult` (a user-role message with a `ContentBlock.ToolResult`), together with the conversation so far:

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.9.0

import sttp.ai.claude.ClaudeSyncClient
import sttp.ai.claude.models.{ClaudeModel, ContentBlock, Message, PropertySchema, Tool, ToolInputSchema}
import sttp.ai.claude.requests.MessageRequest

object ToolRoundTrip:
  def main(args: Array[String]): Unit =
    val claude = ClaudeSyncClient.fromEnv
    try {
      val weatherTool = Tool(
        name = "get_weather",
        description = "Get current weather for a location",
        inputSchema = ToolInputSchema(
          `type` = "object",
          properties = Map("location" -> PropertySchema(`type` = "string", description = Some("City name"))),
          required = Some(List("location"))
        )
      )

      val question = Message.user(List(ContentBlock.text("What's the weather in Paris?")))

      val first = claude.createMessage(
        MessageRequest.withTools(
          model = ClaudeModel.ClaudeHaiku4_5.value,
          messages = List(question),
          maxTokens = 1000,
          tools = List(weatherTool)
        )
      )

      val toolUses = first.content.collect { case tu: ContentBlock.ToolUse => tu }

      val toolResults = toolUses.map { tu =>
        // ... execute the tool yourself, e.g. look up the weather for tu.input("location")
        Message.toolResult(tu.id, "22C, sunny")
      }

      val second = claude.createMessage(
        MessageRequest.withTools(
          model = ClaudeModel.ClaudeHaiku4_5.value,
          messages = List(question, Message.assistant(first.content)) ++ toolResults,
          maxTokens = 1000,
          tools = List(weatherTool)
        )
      )

      second.content.foreach {
        case t: ContentBlock.Text => println(t.text)
        case _                    => ()
      }
    } finally claude.close()
```

## Predefined tools

Currently supported:

- **`Tool.WebSearch`** (`web_search_20250305`)

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.9.0

import sttp.ai.claude.ClaudeSyncClient
import sttp.ai.claude.models.{ClaudeModel, ContentBlock, Message, Tool}
import sttp.ai.claude.requests.MessageRequest

object WebSearchExample:
  def main(args: Array[String]): Unit =
    val claude = ClaudeSyncClient.fromEnv
    try {
      val request = MessageRequest.withTools(
        model = ClaudeModel.ClaudeSonnet5.value,
        messages = List(Message.user(List(ContentBlock.text("What was the most recent SpaceX launch?")))),
        maxTokens = 1024,
        tools = List(Tool.WebSearch.default)
      )

      val response = claude.createMessage(request)

      response.content.foreach {
        case t: ContentBlock.Text => println(t.text)
        case s: ContentBlock.ServerToolUse =>
          println(s"Searched for: ${s.input.get("query").flatMap(_.asString).getOrElse("")}")
        case r: ContentBlock.WebSearchToolResult =>
          r.content match {
            case ContentBlock.WebSearchToolResultBlock.Results(items) =>
              items.foreach(it => println(s"- ${it.title} — ${it.url}"))
            case ContentBlock.WebSearchToolResultBlock.Error(code) =>
              println(s"Web search failed: $code")
          }
        case _ => ()
      }
    } finally claude.close()
```

Both custom and predefined tools can be passed in the same `tools` list.

## Using the agent loop

For a full automatic tool-calling loop — the model calls a tool, your code runs it, the result is fed back, repeat until the model produces a final answer — use `ClaudeAgent` with type-safe `AgentTool`s instead of driving `createMessage` by hand: see the [agent loop](../agents/quickstart.md).
