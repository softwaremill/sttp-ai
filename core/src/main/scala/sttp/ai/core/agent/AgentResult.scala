package sttp.ai.core.agent

sealed trait FinishReason

object FinishReason {
  case object MaxIterations extends FinishReason
  case object NaturalStop extends FinishReason
  case object TokenLimit extends FinishReason
  case class Error(message: String) extends FinishReason
}

final case class ToolCallRecord(
    id: String,
    toolName: String,
    input: String,
    output: String,
    iteration: Int
)

final case class AgentResult[T](
    finalAnswer: T,
    iterations: Int,
    toolCalls: Seq[ToolCallRecord],
    finishReason: FinishReason
)

final case class AgentDecodeError(
    rawResult: AgentResult[String],
    cause: Throwable
) extends Exception(s"Failed to decode the agent's final answer as the requested type: ${cause.getMessage}", cause)
