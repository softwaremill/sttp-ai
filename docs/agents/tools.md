# Agent tools and results

## Defining tools

Tools are defined using type-safe case classes with the `derives` syntax:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

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
  finishReason: FinishReason  // MaxIterations | NaturalStop | TokenLimit | ForcedStop (BudgetExceeded / Custom) | Error(message)
)
```

`agent.run(prompt)(backend)` returns `AgentResult[Either[AgentFailure, String]]` by default. See below for typed input and output.

## Typed input and output

A fresh builder starts at `Agent[F, String, String]`: plain-text prompt in, plain-text answer out. `deriveResponseSchema[T]`
transitions the builder's `Out` type to `T`, so the built agent's `run` returns `AgentResult[Either[AgentFailure, T]]`
instead. The response schema, derived from `T`, is sent to the model to define the structured output of the agent's
final answer; the answer is then parsed back into `T` via circe.

On failure the iteration trace is preserved: `finalAnswer` is a `Left(AgentFailure)` rather than a thrown exception. There are two failure cases:

- `AgentParseError` - the loop stopped naturally but the answer couldn't be parsed as `T`.
- `AgentIncomplete` - the loop was cut short (`FinishReason.MaxIterations`, an interceptor-forced `FinishReason.ForcedStop` such as `BudgetExceeded`/`Custom`, or `FinishReason.TokenLimit`). On `MaxIterations` and on a forced stop, a parse is still attempted (the final iteration forces a schema-guided answer without tools), and `parseError` carries the cause if it failed; on `TokenLimit` the answer is truncated, so no parse is attempted and `parseError` is `None`.

Note that because `MaxIterations` and forced stops still parse, `finalAnswer` can be `Right(t)` even though the run was capped or cut short by an interceptor — check `AgentResult.finishReason` if you need to distinguish that from a natural stop.

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

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
    agent.run("What's the weather in Paris?")(backend).finalAnswer match {
      case Right(summary)                              => println(s"Weather: ${summary.weather}")
      case Left(AgentParseError(raw, cause))           => println(s"Parse failed: ${cause.getMessage}; raw=$raw")
      case Left(AgentIncomplete(raw, finishReason, _)) => println(s"Run incomplete ($finishReason); raw=$raw")
    }
  } finally backend.close()
}
```

The same `deriveResponseSchema[T]` works with `ClaudeAgent.synchronous(...)`.

Input can be typed the same way. `input[In]` transitions the builder's `In` type, rendering the value into the first
user message as compact JSON (via its circe `Encoder`) wrapped in a small fixed envelope; `inputRenderer[In]` takes
an explicit `In => String` function when you want control over how the value is rendered.

## Composing agents

`andThen` chains two agents so the second starts a fresh conversation from the first's typed output: it only
compiles when the first agent's `Out` matches the second's `In`, so a mismatched handoff is a compile error, not a
runtime surprise.

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.tapir.Schema

val openai = OpenAI.fromEnv

case class Location(city: String) derives io.circe.Codec.AsObject, Schema

val planner = OpenAIAgent
  .synchronous(openai, ChatCompletionModel.GPT4oMini)
  .deriveResponseSchema[Location]
  .build // Agent[Identity, String, Location]

val guide = OpenAIAgent
  .synchronous(openai, ChatCompletionModel.GPT4oMini)
  .input[Location] // rendered into the first user message as JSON
  .build // Agent[Identity, Location, String]

val trip = planner.andThen(guide) // Agent[Identity, String, String] — compile-checked handoff
```

Each stage runs its own complete loop from scratch, seeded only with the previous stage's rendered output — no
conversation history carries over. If a stage fails (`Left`), the chain short-circuits and later stages never run;
on success, `iterations`, `toolCalls`, `usage`, and `llmCalls` aggregate across every stage that ran. Interceptors
are stage-local too: each agent keeps the interceptors it was built with, so a budget interceptor bounds its own
stage's spend, not the whole chain's.

When two agents are almost but not quite compatible, reach for `map` (adapt the output) or `contramap` (adapt the
input) instead of rebuilding either agent.
