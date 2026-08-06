package sttp.ai.core.agent

/** Middleware for the agent loop. Interceptors wrap iterations, LLM calls, and tool executions (onion-style, like sttp backend wrappers),
  * and can steer the loop via [[decide]].
  *
  * Contracts:
  *   - `next` is by-name: with `F = Identity`, `F[A] = A`, so a strict parameter would run the stage before the interceptor could act.
  *     Interceptors that skip calling `next` short-circuit the stage.
  *   - Exceptions raised by interceptor code propagate in `F` and fail the run. Interceptors are trusted infrastructure; their errors are
  *     never swallowed.
  *   - [[decide]] is pure: it is a judgment over already-accumulated state, consulted before each iteration. Effects belong in the
  *     `around*` methods.
  */
trait AgentInterceptor[F[_]] {

  /** Wraps one full loop iteration (LLM call + any tool executions). `A` is the loop's internal outcome type, opaque on purpose. */
  def aroundIteration[A](ctx: IterationContext)(next: => F[A]): F[A] = next

  /** Wraps a single LLM request. The returned [[AgentResponse]] carries provider-reported `usage` and `model` when available. */
  def aroundLlmCall(ctx: LlmCallContext)(next: => F[AgentResponse]): F[AgentResponse] = next

  /** Wraps a single tool execution (input decoding, tool body, error handling). */
  def aroundToolCall(ctx: ToolCallContext)(next: => F[ToolCallRecord]): F[ToolCallRecord] = next

  /** Consulted by the loop before each iteration. Return [[LoopDecision.FinishNow]] to force a graceful final answer. */
  def decide(state: AgentRunState): LoopDecision = LoopDecision.Continue
}

object AgentInterceptor {

  /** An interceptor that does nothing: all stages pass through, `decide` always continues. */
  def noop[F[_]]: AgentInterceptor[F] = new AgentInterceptor[F] {}

  /** Composes interceptors: the FIRST interceptor in the list is OUTERMOST for `around*` stages; for `decide`, interceptors are consulted
    * in list order and the first [[LoopDecision.FinishNow]] wins.
    */
  def compose[F[_]](interceptors: Seq[AgentInterceptor[F]]): AgentInterceptor[F] =
    interceptors.reduceOption(combine[F]).getOrElse(noop[F])

  private def combine[F[_]](outer: AgentInterceptor[F], inner: AgentInterceptor[F]): AgentInterceptor[F] =
    new AgentInterceptor[F] {
      override def aroundIteration[A](ctx: IterationContext)(next: => F[A]): F[A] =
        outer.aroundIteration(ctx)(inner.aroundIteration(ctx)(next))

      override def aroundLlmCall(ctx: LlmCallContext)(next: => F[AgentResponse]): F[AgentResponse] =
        outer.aroundLlmCall(ctx)(inner.aroundLlmCall(ctx)(next))

      override def aroundToolCall(ctx: ToolCallContext)(next: => F[ToolCallRecord]): F[ToolCallRecord] =
        outer.aroundToolCall(ctx)(inner.aroundToolCall(ctx)(next))

      override def decide(state: AgentRunState): LoopDecision =
        outer.decide(state) match {
          case finish: LoopDecision.FinishNow => finish
          case LoopDecision.Continue          => inner.decide(state)
        }
    }
}

/** Loop position of the iteration being wrapped. */
final case class IterationContext(iterationInfo: IterationInfo)

/** What the loop is about to send to the LLM. */
final case class LlmCallContext(history: ConversationHistory, includeTools: Boolean, iterationInfo: IterationInfo)

/** The tool call about to be executed, and the 1-based iteration it belongs to. */
final case class ToolCallContext(toolCall: ToolCall, iteration: Int)

/** Accumulated run state visible to [[AgentInterceptor.decide]].
  *
  * @param iterationsCompleted
  *   number of fully completed iterations (0 before the first)
  * @param llmCalls
  *   per-call usage breakdown; enables per-model cost budgets when the run mixes models
  */
final case class AgentRunState(
    iterationsCompleted: Int,
    maxIterations: Int,
    usage: TokenUsage,
    llmCalls: Seq[LlmCallUsage]
)

/** What the loop should do next, as judged by an interceptor before an iteration. */
sealed trait LoopDecision

object LoopDecision {
  case object Continue extends LoopDecision

  /** Force a graceful final answer: `instruction` is injected as a user message, tools are withheld (the existing last-iteration
    * mechanism), and the run finishes with `finishReason = cause`. If the loop is simultaneously at its forced last iteration,
    * `MaxIterations` takes precedence as the reported reason. If the forced-final LLM response itself stops with `StopReason.MaxTokens`,
    * that check precedes the `FinishNow` cause and the run reports `FinishReason.TokenLimit` instead.
    */
  final case class FinishNow(cause: FinishReason, instruction: String) extends LoopDecision
}
