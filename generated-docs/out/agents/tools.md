# Agent tools and results

## Defining tools

Tools are defined using type-safe case classes with the `derives` syntax:

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.8.0

import sttp.ai.core.agent.*
import sttp.tapir.Schema

case class CalculatorInput(
  operation: String,
  a: Double,
  b: Double
) derives io.circe.Codec.AsObject, Schema

val calculatorTool = AgentTool.fromFunction(
  "calculate",
  "Perform a mathematical calculation"
) { (input: CalculatorInput) =>
  input.operation match {
    case "add"      => s"${input.a + input.b}"
    case "subtract" => s"${input.a - input.b}"
    case "multiply" => s"${input.a * input.b}"
    case "divide"   => 
      if (input.b != 0) s"${input.a / input.b}" 
      else "Error: Division by zero"
  }
}
```

The `derives io.circe.Codec.AsObject, Schema` clause automatically generates the necessary serialization and schema information for the tool.

See [JSON Schemas: structured outputs & tools](../other/json-schemas.md) for how schema derivation works and how to customise it.

## Agent Result

```scala
case class AgentResult[T](
  finalAnswer: T,
  iterations: Int,
  toolCalls: Seq[ToolCallRecord],
  finishReason: FinishReason  // MaxIterations | NaturalStop | TokenLimit | Error(message)
)
```

`agent.run(prompt)(backend)` returns `AgentResult[String]`. For typed results, see `runAs[T]` below.

## Typed responses with `runAs[T]`

Set `responseSchema` on `AgentConfig` and use `runAs[T]` to receive a parsed Scala value as the agent's final answer. The response schema, derived from `T`, is sent to the model to define the structured output of the agent's final answer. The answer is then parsed back into `T` via circe.

On failure the iteration trace is preserved: `finalAnswer` is a `Left(AgentFailure)` rather than a thrown exception. There are two failure cases:

- `AgentParseError` - the loop stopped naturally but the answer couldn't be parsed as `T`.
- `AgentIncomplete` - the loop was cut short (`FinishReason.MaxIterations` or `FinishReason.TokenLimit`). On `MaxIterations` a parse is still attempted (the final iteration forces a schema-guided answer without tools), and `parseError` carries the cause if it failed; on `TokenLimit` the answer is truncated, so no parse is attempted and `parseError` is `None`.

Note that because `MaxIterations` still parses, `finalAnswer` can be `Right(t)` even though the run hit the iteration cap - check `AgentResult.finishReason` if you need to distinguish a capped run from a natural stop.

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.8.0

import sttp.ai.core.agent.*
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.client4.DefaultSyncBackend
import sttp.tapir.Schema

case class TripSummary(weather: String, calculation: String, conclusion: String) derives io.circe.Codec.AsObject, Schema
case class WeatherInput(location: String) derives io.circe.Codec.AsObject, Schema

object TypedAgentExample extends App {
  val weatherTool = AgentTool.fromFunction("get_weather", "Get the current weather for a location") {
    (input: WeatherInput) => s"The weather in ${input.location} is 22°C, sunny"
  }

  val backend = DefaultSyncBackend()
  try {
    val agent = OpenAIAgent
      .synchronous(OpenAI.fromEnv, "gpt-4o-mini")
      .maxIterations(5)
      .tools(weatherTool)
      .deriveResponseSchema[TripSummary]
      .build
    agent.runAs[TripSummary]("What's the weather in Paris?")(backend).finalAnswer match {
      case Right(summary)                              => println(s"Weather: ${summary.weather}")
      case Left(AgentParseError(raw, cause))           => println(s"Parse failed: ${cause.getMessage}; raw=$raw")
      case Left(AgentIncomplete(raw, finishReason, _)) => println(s"Run incomplete ($finishReason); raw=$raw")
    }
  } finally backend.close()
}
```

The same `runAs[T]` works against `ClaudeAgent.synchronous(...)`.
