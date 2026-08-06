package sttp.ai.core.agent.interceptor

import sttp.ai.core.agent._
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps

/** Severity of a [[LoggingInterceptor]] message. A minimal in-house ADT so core gains no logging dependency. */
sealed trait LogLevel

object LogLevel {
  case object Debug extends LogLevel
  case object Info extends LogLevel
  case object Warn extends LogLevel
}

/** Logs agent-loop activity through a user-supplied sink, keeping core free of logging dependencies and the effect referentially
  * transparent. Bridge examples (slf4j/log4cats, otel4s, ZIO logging) are in the interceptors documentation page.
  */
final class LoggingInterceptor[F[_]](sink: (LogLevel, String) => F[Unit])(implicit monad: MonadError[F]) extends AgentInterceptor[F] {

  override def aroundIteration[A](ctx: IterationContext)(next: => F[A]): F[A] = {
    val position = s"${ctx.iterationInfo.iteration}/${ctx.iterationInfo.maxIterations}"
    sink(LogLevel.Debug, s"Iteration $position started").flatMap { _ =>
      next.flatMap(result => sink(LogLevel.Debug, s"Iteration $position finished").map(_ => result))
    }
  }

  override def aroundLlmCall(ctx: LlmCallContext)(next: => F[AgentResponse]): F[AgentResponse] =
    next.flatMap { response =>
      val model = response.model.getOrElse("unknown")
      val usage = response.usage.getOrElse(TokenUsage.Zero)
      sink(
        LogLevel.Info,
        s"LLM call (iteration ${ctx.iterationInfo.iteration}, model $model): " +
          s"input=${usage.inputTokens.value} output=${usage.outputTokens.value} tokens"
      ).map(_ => response)
    }

  override def aroundToolCall(ctx: ToolCallContext)(next: => F[ToolCallRecord]): F[ToolCallRecord] =
    sink(LogLevel.Info, s"Tool call started: ${ctx.toolCall.toolName}").flatMap { _ =>
      next.flatMap(record => sink(LogLevel.Info, s"Tool call finished: ${record.toolName}").map(_ => record))
    }
}
