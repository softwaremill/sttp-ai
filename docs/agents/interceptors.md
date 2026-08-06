# Interceptors

Interceptors are composable middleware for the agent loop: they wrap iterations, LLM calls, and tool executions
(onion-style, like sttp backend wrappers), observe provider-reported token usage, and can end the loop gracefully
when a budget is exhausted.

## Writing an interceptor

Extend `AgentInterceptor[F]` and override only the stages you need. All methods default to pass-through.

```scala mdoc:compile-only
import sttp.ai.core.agent.*
import sttp.shared.Identity

val timing = new AgentInterceptor[Identity]:
  override def aroundToolCall(ctx: ToolCallContext)(next: => Identity[ToolCallRecord]): Identity[ToolCallRecord] =
    val start = System.nanoTime()
    val record = next
    println(s"${ctx.toolCall.toolName} took ${(System.nanoTime() - start) / 1000000} ms")
    record
```

Rules of the trade:

- `next` is by-name — don't force it before you mean to run the stage.
- Exceptions thrown by interceptor code fail the whole run; they are never swallowed.
- `decide` is pure: it judges the accumulated `AgentRunState` before each iteration and can return
  `LoopDecision.FinishNow(cause, instruction)` to force a graceful final answer.

## Composition and ordering

Add interceptors on the builder. The first added is outermost for `around*` stages; for `decide`, interceptors are
consulted in order and the first `FinishNow` wins:

```scala mdoc:compile-only
import sttp.ai.core.agent.*
import sttp.ai.core.agent.interceptor.*
import sttp.ai.openai.agent.OpenAIAgent
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.monad.IdentityMonad
import sttp.shared.Identity

val agent = OpenAIAgent
  .synchronous("api-key", ChatCompletionModel.GPT4oMini)
  .interceptor(new LoggingInterceptor[Identity]((level, msg) => println(s"[$level] $msg"))(IdentityMonad))
  .interceptor(new BudgetInterceptor[Identity](maxTotalTokens = Some(Tokens(200_000L))))
  .build
```

## Usage accounting

Every LLM call's provider-reported usage lands on the response and accumulates into the result:

```scala mdoc:compile-only
import sttp.ai.core.agent.*

def report(result: AgentResult[String]): Unit =
  println(s"total tokens: ${result.usage.totalTokens.value}")
  result.llmCalls.foreach { call =>
    println(s"${call.model.getOrElse("?")}: in=${call.usage.inputTokens.value} out=${call.usage.outputTokens.value}")
  }
```

`AgentResult.finishReason` reports why the loop stopped: `NaturalStop`, `MaxIterations`, `TokenLimit`,
`BudgetExceeded`, or `Error`.

## Budgets

`BudgetInterceptor` ends the loop gracefully — it injects a final-answer instruction and withholds tools, mirroring
the last-iteration behavior — instead of failing:

```scala mdoc:compile-only
import sttp.ai.core.agent.*
import sttp.ai.core.agent.interceptor.*
import sttp.shared.Identity

val prices = PriceTable(Map(
  "gpt-4o-mini" -> ModelPrice(inputPerMTok = BigDecimal("0.15"), outputPerMTok = BigDecimal("0.60"))
))

val budget = new BudgetInterceptor[Identity](
  maxTotalTokens = Some(Tokens(200_000L)),
  maxCost = Some(Cost(BigDecimal("2.50"))),
  priceTable = Some(prices)
)
```

The library ships no prices — supply your own table, keyed by the provider-reported model id. **Calls whose model id
is missing from the table contribute zero to the cost check**; prefer `maxTotalTokens` (which needs no table) when
not every model in play is priced.

## Bridging the logging sink

`LoggingInterceptor` takes a sink `(LogLevel, String) => F[Unit]`, so core needs no logging dependency. It requires
an implicit `MonadError[F]` in scope (for `Identity`, `sttp.monad.IdentityMonad`).

slf4j via log4cats:

```scala
import cats.effect.IO
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.ai.core.agent.interceptor.{LoggingInterceptor, LogLevel}

val logger = Slf4jLogger.getLogger[IO]
val logging = new LoggingInterceptor[IO]({
  case (LogLevel.Debug, msg) => logger.debug(msg)
  case (LogLevel.Info, msg)  => logger.info(msg)
  case (LogLevel.Warn, msg)  => logger.warn(msg)
})
```

ZIO logging:

```scala
import sttp.ai.core.agent.interceptor.{LoggingInterceptor, LogLevel}
import zio.*

val logging = new LoggingInterceptor[Task]({
  case (LogLevel.Debug, msg) => ZIO.logDebug(msg)
  case (LogLevel.Info, msg)  => ZIO.logInfo(msg)
  case (LogLevel.Warn, msg)  => ZIO.logWarning(msg)
})
```

For tracing (otel4s, ZIO Telemetry), wrap stages in spans with a custom interceptor instead of the logging sink:

```scala
import org.typelevel.otel4s.trace.Tracer
import sttp.ai.core.agent.*

class TracingInterceptor[F[_]: Tracer] extends AgentInterceptor[F]:
  override def aroundLlmCall(ctx: LlmCallContext)(next: => F[AgentResponse]): F[AgentResponse] =
    Tracer[F].span("agent.llm-call").surround(next)
```

## Migrating from the tool-call hooks

`hookBeforeToolCall` / `hookAfterToolCall` are deprecated (since 0.8.0) and will be removed in the following release.
They keep working for now and run innermost (closest to the tool call). The equivalent interceptor:

```scala mdoc:compile-only
import sttp.ai.core.agent.*
import sttp.shared.Identity

val hooks = new AgentInterceptor[Identity]:
  override def aroundToolCall(ctx: ToolCallContext)(next: => Identity[ToolCallRecord]): Identity[ToolCallRecord] =
    println(s"before ${ctx.toolCall.toolName}")
    val record = next
    println(s"after ${record.toolName}")
    record
```
