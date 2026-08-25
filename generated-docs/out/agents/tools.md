# Agent tools and results

## Defining tools

Tools are defined using type-safe case classes with the `derives` syntax:

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.9.0

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

## Deriving tool sets from a service trait

On Scala 3, a whole tool set can be derived from a service trait's methods with `AgentTools.derive` — one tool per public method, without defining an input case class per tool:

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.9.0

import sttp.ai.core.agent.*
import sttp.tapir.Schema.annotations.description

trait WeatherService:
  @description("Get the current weather for a city")
  def currentWeather(@description("City name") city: String): String

  @description("Get a multi-day forecast")
  def forecast(city: String, days: Int): String

class WeatherServiceImpl extends WeatherService:
  def currentWeather(city: String): String = s"Sunny in $city"
  def forecast(city: String, days: Int): String = s"$days days of sun in $city"

val tools = AgentTools.derive[WeatherService](WeatherServiceImpl())
// two AgentTools: "currentWeather" and "forecast"
```

The rules:

- Every public method with a single (possibly empty) parameter list becomes a tool; methods inherited from parent traits are included. Parameterless accessors (`def foo: String`, `val`s, `var`s) are skipped — unless a parameterless `def` carries `@description`, which is a compile error (add `()` to expose it as a tool).
- The tool name is the method name; JSON schema properties are the parameter names, with types taken from each parameter's tapir `Schema`. `Option` parameters become optional properties. Case-class parameters work out of the box (given their own `Schema`/`Codec` instances); their schemas are referenced via `$defs`.
- Each method must carry a `@description` annotation (`sttp.tapir.Schema.annotations.description`) — it becomes the tool description. Parameter-level `@description` annotations become property descriptions. The annotation argument can be a string literal or any compile-time constant string (e.g. a `final val`).
- Methods must return `String`. For effectful services use `AgentTools.deriveF[F, MyService]` with methods returning `F[String]` (covariantly narrowed return types, e.g. `Some[String]` for `F = Option`, conform).
- Invalid shapes — a missing `@description`, overloads, default parameter values, multiple/`using` parameter lists, type parameters, by-name or vararg parameters, or a parameter type without given `Schema`/`Decoder`/`Encoder` instances — are compile-time errors.
- All inherited public methods must conform to these rules — there is no per-method exclusion mechanism — so a service trait should not mix tool methods with unrelated public methods (e.g. extending `AutoCloseable` won't work).
- Duplicate tool names are rejected within one trait (no overloads), but when combining tool sets derived from several traits, keeping names unique across the combined set is the caller's responsibility.

Derivation is **Scala 3 only**: on Scala 2.13, define tools individually with `AgentTool.fromFunction` as shown above.

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

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.9.0

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

Response schemas also support discriminated unions — `UnionResponseSchema.derive[A | B | C]` on Scala 3, or
`ResponseSchema.oneOf` with explicit variants for sealed traits and Scala 2.13 (see
[JSON Schemas](../other/json-schemas.md) for the wire shape). A classifier agent then routes each intent to a
typed sub-agent, with unhandled intents caught by the compiler's exhaustivity check:

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.9.0

import io.circe.Codec
import sttp.ai.core.agent.*
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.client4.DefaultSyncBackend
import sttp.tapir.Schema

final case class Refund(orderId: String) derives Codec.AsObject, Schema
final case class Complaint(topic: String) derives Codec.AsObject, Schema
final case class GeneralQuery() derives Codec.AsObject, Schema

val backend = DefaultSyncBackend()
val openai = OpenAI.fromEnv

val classifier = OpenAIAgent
  .synchronous(openai, "gpt-4o-mini")
  .responseSchema(UnionResponseSchema.derive[Refund | Complaint | GeneralQuery]("Classify the user's intent"))
  .build

val answer = classifier.run("I want my money back for order o-1")(backend).finalAnswer.map {
  case r: Refund       => s"routing to refunds: ${r.orderId}"
  case c: Complaint    => s"routing to support: ${c.topic}"
  case _: GeneralQuery => "routing to FAQ"
}
```

## Composing agents

`andThen` chains two agents so the second starts a fresh conversation from the first's typed output: it only
compiles when the first agent's `Out` matches the second's `In`, so a mismatched handoff is a compile error, not a
runtime surprise.

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.9.0

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
