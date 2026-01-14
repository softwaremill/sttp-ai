package sttp.ai.core.agent

sealed trait FinishReason

object FinishReason {
  case object MaxIterations extends FinishReason
  case object ToolFinish extends FinishReason
  case object NaturalStop extends FinishReason
  case class Error(message: String) extends FinishReason
}

case class ToolCallRecord(
    toolName: String,
    input: String,
    output: String,
    iteration: Int
)

case class AgentResult[T](
    finalAnswer: T,
    iterations: Int,
    toolCalls: Seq[ToolCallRecord],
    finishReason: FinishReason
)
