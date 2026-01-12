package sttp.ai.core.agent

import sttp.client4.Backend

trait AgentBackend[F[_]] {
  def sendRequest(
      history: ConversationHistory,
      backend: Backend[F]
  ): F[AgentResponse]
}

case class AgentResponse(
    textContent: String,
    toolCalls: Seq[ToolCall],
    stopReason: Option[String]
)
