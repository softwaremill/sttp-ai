package sttp.ai.core.agent

/** A count of LLM tokens. A dedicated type so token counts cannot be confused with other numeric quantities (costs, iteration counts). */
final case class Tokens(value: Long) extends AnyVal {
  def +(other: Tokens): Tokens = Tokens(value + other.value)
  def <(other: Tokens): Boolean = value < other.value
  def <=(other: Tokens): Boolean = value <= other.value
  def >(other: Tokens): Boolean = value > other.value
  def >=(other: Tokens): Boolean = value >= other.value
}

object Tokens {
  val Zero: Tokens = Tokens(0L)
}

/** Provider-normalized token usage of one or more LLM calls.
  *
  * @param inputTokens
  *   ALL prompt-side tokens — cached reads and cache writes included
  * @param outputTokens
  *   ALL completion-side tokens, reasoning tokens included
  * @param cachedInputTokens
  *   informational subset of [[inputTokens]] served from a provider cache (cache reads)
  * @param reasoningTokens
  *   informational subset of [[outputTokens]] spent on reasoning/thinking, where the provider reports it
  * @param cacheWriteInputTokens
  *   informational subset of [[inputTokens]] written to a provider cache (cache writes, e.g. Claude's `cache_creation_input_tokens`); often
  *   billed at a premium over the plain input rate
  */
final case class TokenUsage(
    inputTokens: Tokens,
    outputTokens: Tokens,
    cachedInputTokens: Tokens,
    reasoningTokens: Tokens,
    cacheWriteInputTokens: Tokens = Tokens.Zero
) {
  def totalTokens: Tokens = inputTokens + outputTokens

  def +(other: TokenUsage): TokenUsage = TokenUsage(
    inputTokens = inputTokens + other.inputTokens,
    outputTokens = outputTokens + other.outputTokens,
    cachedInputTokens = cachedInputTokens + other.cachedInputTokens,
    reasoningTokens = reasoningTokens + other.reasoningTokens,
    cacheWriteInputTokens = cacheWriteInputTokens + other.cacheWriteInputTokens
  )
}

object TokenUsage {
  val Zero: TokenUsage = TokenUsage(Tokens.Zero, Tokens.Zero, Tokens.Zero, Tokens.Zero, Tokens.Zero)
}

/** Usage of a single LLM call, with the model that served it (as reported by the provider) for per-model cost calculation. */
final case class LlmCallUsage(model: Option[String], usage: TokenUsage)
