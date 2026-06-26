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

sealed trait AgentResult[+T] {
  def rawAnswer: String
  def iterations: Int
  def toolCalls: Seq[ToolCallRecord]
  def finishReason: FinishReason
}

object AgentResult {

  final case class FinalAnswer[T](
      answer: T,
      rawAnswer: String,
      iterations: Int,
      toolCalls: Seq[ToolCallRecord]
  ) extends AgentResult[T] {
    override val finishReason: FinishReason = FinishReason.NaturalStop
  }

  final case class Incomplete(
      rawAnswer: String,
      iterations: Int,
      toolCalls: Seq[ToolCallRecord],
      finishReason: FinishReason,
      cause: Option[Throwable] = None
  ) extends AgentResult[Nothing]
}

final case class AgentDecodeError(
    cause: Throwable
) extends Exception(s"Failed to decode the agent's final answer as the requested type: ${cause.getMessage}", cause)
