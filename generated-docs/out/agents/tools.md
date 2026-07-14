# Agent tools and results

Tools are defined using type-safe case classes with the `derives` syntax:

```scala
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

## Agent Result

```scala
case class AgentResult[T](
  finalAnswer: T,
  iterations: Int,
  toolCalls: Seq[ToolCallRecord],
  finishReason: FinishReason  // MaxIterations | NaturalStop | TokenLimit | Errpr
)
```

`agent.run(prompt)(backend)` returns `AgentResult[String]`. For typed results, see `runAs[T]` below.

## Typed responses with `runAs[T]`

Set `responseSchema` on `AgentConfig` and use `runAs[T]` to receive a parsed Scala value as the agent's final answer. The response schema, derived from `T`, is sent to the model to define the structured output of the agent's final answer. The answer is then parsed back into `T` via circe.

On parse failure the iteration trace is preserved: `finalAnswer` is `Left(AgentParseError)` rather than a thrown exception.

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.5.1+19-83e9d91a+20260714-1032-SNAPSHOT

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
      case Right(summary) => println(s"Weather: ${summary.weather}")
      case Left(err)      => println(s"Parse failed: ${err.cause.getMessage}; raw=${err.rawAnswer}")
    }
  } finally backend.close()
}
```

The same `runAs[T]` works against `ClaudeAgent.synchronous(...)`.
