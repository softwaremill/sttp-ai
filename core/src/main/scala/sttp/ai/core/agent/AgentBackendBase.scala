package sttp.ai.core.agent

import sttp.client4.Backend

abstract class AgentBackendBase[F[_]](
  val tools: Seq[AgentTool],
  val systemPrompt: Option[String]
) extends AgentBackend[F] {

  final override def sendRequest(
    history: ConversationHistory,
    backend: Backend[F]
  ): F[AgentResponse] = {
    sendApiRequest(history, backend)
  }

  protected def sendApiRequest(
    history: ConversationHistory,
    backend: Backend[F]
  ): F[AgentResponse]
}
