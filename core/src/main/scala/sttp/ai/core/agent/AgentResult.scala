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

sealed trait AgentFailure {
  def rawAnswer: String
}

final case class AgentParseError(
    rawAnswer: String,
    cause: Throwable
) extends AgentFailure

final case class AgentIncomplete(
    rawAnswer: String,
    finishReason: FinishReason,
    parseError: Option[Throwable]
) extends AgentFailure
