package sttp.ai.core.agent

import sttp.client4.Backend
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps

/** An agent with typed input `In` and typed output `Out`, runnable against an sttp backend.
  *
  * The loop-based implementation is produced by [[AgentBuilder.build]]. API/transport errors surface in `F`'s error channel; agent-level
  * outcomes (unparseable or incomplete answers) are `Left[AgentFailure]` values inside the result.
  */
trait Agent[F[_], In, Out] { self =>

  protected implicit def monad: MonadError[F]

  def run(in: In)(backend: Backend[F]): F[AgentResult[Either[AgentFailure, Out]]]

  /** Typed handoff: runs `this`, then feeds its `Out` to `next` as a fresh conversation (rendered by `next`'s input renderer). Compiles
    * only when this agent's output type is `next`'s input type. If `this` fails (`Left`), `next` never runs. Metadata is aggregated:
    * iterations and usage summed, tool and LLM calls concatenated in stage order, `finishReason` taken from the last stage that ran.
    * `ToolCallRecord.iteration` stays stage-local. If `next` raises an error in `F`'s error channel, it propagates as a plain error and
    * `this` stage's accumulated metadata (usage, tool calls) is not reported in that case. Interceptors are also stage-local: each agent
    * runs with its own configured interceptors, so e.g. a budget interceptor on `next` does not observe this stage's token spend.
    */
  def andThen[Out2](next: Agent[F, Out, Out2]): Agent[F, In, Out2] = {
    val m = monad
    new Agent[F, In, Out2] {
      protected implicit def monad: MonadError[F] = m
      def run(in: In)(backend: Backend[F]): F[AgentResult[Either[AgentFailure, Out2]]] =
        self.run(in)(backend).flatMap { first =>
          first.finalAnswer match {
            case Left(failure) =>
              m.unit(
                AgentResult[Either[AgentFailure, Out2]](
                  Left(failure),
                  first.iterations,
                  first.toolCalls,
                  first.finishReason,
                  first.usage,
                  first.llmCalls
                )
              )
            case Right(out) =>
              next.run(out)(backend).map { second =>
                AgentResult(
                  second.finalAnswer,
                  first.iterations + second.iterations,
                  first.toolCalls ++ second.toolCalls,
                  second.finishReason,
                  first.usage + second.usage,
                  first.llmCalls ++ second.llmCalls
                )
              }
          }
        }
    }
  }

  /** Adapts the output with a pure function, applied to `Right` results only; failures and metadata pass through untouched. */
  def map[Out2](f: Out => Out2): Agent[F, In, Out2] = {
    val m = monad
    new Agent[F, In, Out2] {
      protected implicit def monad: MonadError[F] = m
      def run(in: In)(backend: Backend[F]): F[AgentResult[Either[AgentFailure, Out2]]] =
        self.run(in)(backend).map { res =>
          AgentResult(res.finalAnswer.map(f), res.iterations, res.toolCalls, res.finishReason, res.usage, res.llmCalls)
        }
    }
  }

  /** Adapts the input with a pure function, applied before the agent runs. */
  def contramap[In2](f: In2 => In): Agent[F, In2, Out] = {
    val m = monad
    new Agent[F, In2, Out] {
      protected implicit def monad: MonadError[F] = m
      def run(in: In2)(backend: Backend[F]): F[AgentResult[Either[AgentFailure, Out]]] =
        self.run(f(in))(backend)
    }
  }
}

object Agent {

  /** Constructs the loop-based agent with untyped (string) input and output — the shape produced by a fresh [[AgentBuilder]]. */
  def apply[F[_]](
      agentBackend: AgentBackend[F],
      config: AgentConfig[F]
  )(implicit monad: MonadError[F]): Agent[F, String, String] =
    new LoopAgent[F, String, String](agentBackend, config, identity, answer => Right(answer))
}
