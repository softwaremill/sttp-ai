package sttp.ai.core.agent

import sttp.client4.Backend
import sttp.monad.MonadError

/** An agent with typed input `In` and typed output `Out`, runnable against an sttp backend.
  *
  * The loop-based implementation is produced by [[AgentBuilder.build]]. API/transport errors surface in `F`'s error channel; agent-level
  * outcomes (unparseable or incomplete answers) are `Left[AgentFailure]` values inside the result.
  */
trait Agent[F[_], In, Out] {

  protected implicit def monad: MonadError[F]

  def run(in: In)(backend: Backend[F]): F[AgentResult[Either[AgentFailure, Out]]]
}

object Agent {

  /** Constructs the loop-based agent with untyped (string) input and output — the shape produced by a fresh [[AgentBuilder]]. */
  def apply[F[_]](
      agentBackend: AgentBackend[F],
      config: AgentConfig[F]
  )(implicit monad: MonadError[F]): Agent[F, String, String] =
    new LoopAgent[F, String, String](agentBackend, config, identity, answer => Right(answer))
}
