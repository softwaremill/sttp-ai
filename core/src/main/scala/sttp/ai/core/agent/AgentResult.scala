package sttp.ai.core.agent

sealed trait FinishReason

object FinishReason {
  case object MaxIterations extends FinishReason
  case object NaturalStop extends FinishReason
  case object TokenLimit extends FinishReason

  /** Reasons an [[AgentInterceptor]] may force a graceful final answer via [[LoopDecision.FinishNow]]. Narrowing the `FinishNow` cause to
    * this subtype keeps interceptors from misreporting loop-owned reasons (`NaturalStop`, `TokenLimit`, ...), which would silently change
    * how the typed `run` parses the answer.
    */
  sealed trait ForcedStop extends FinishReason

  /** An [[AgentInterceptor]] budget was exhausted and the loop forced a graceful final answer. */
  case object BudgetExceeded extends ForcedStop

  /** A custom [[AgentInterceptor]] forced a graceful final answer for a reason of its own (e.g. a wall-clock deadline). */
  final case class Custom(reason: String) extends ForcedStop

  case class Error(message: String) extends FinishReason
}

final case class ToolCallRecord(
    id: String,
    toolName: String,
    input: String,
    output: String,
    iteration: Int
)

/** @param history
  *   the full conversation accumulated by the run — the seed history passed to `run` (if any), the rendered user message, assistant
  *   responses, tool results, and the final assistant answer. Feed it back into [[Agent.run]] together with a new user message to continue
  *   the conversation. Per-request iteration markers and forced-finish instructions are transient and not part of it; tool calls from a
  *   forced final response are not executed and thus not recorded.
  */
final case class AgentResult[T](
    finalAnswer: T,
    iterations: Int,
    toolCalls: Seq[ToolCallRecord],
    finishReason: FinishReason,
    usage: TokenUsage = TokenUsage.Zero,
    llmCalls: Seq[LlmCallUsage] = Seq.empty,
    history: ConversationHistory = ConversationHistory.empty
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
