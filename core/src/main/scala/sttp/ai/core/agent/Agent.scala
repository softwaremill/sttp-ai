package sttp.ai.core.agent

import sttp.client4.Backend
import sttp.monad.MonadError

class Agent[F[_]](
    agentBackend: AgentBackend[F],
    config: AgentConfig
)(implicit monad: MonadError[F]) {

  private val loop = new AgentLoop[F](agentBackend, config)(monad)

  def run(initialPrompt: String)(backend: Backend[F]): F[AgentResult] =
    loop.run(initialPrompt)(backend)
}

object Agent {
  def apply[F[_]](
      agentBackend: AgentBackend[F],
      config: AgentConfig
  )(implicit monad: MonadError[F]): Agent[F] =
    new Agent[F](agentBackend, config)(monad)
}
