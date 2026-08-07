package sttp.ai.core.agent.interceptor

import sttp.ai.core.agent._

/** Price of one model per million tokens, in the currency of the user's choosing (consistent across the table).
  *
  * @param cachedInputPerMTok
  *   when set, cached input tokens (cache reads) are priced at this rate instead of [[inputPerMTok]]
  * @param cacheWriteInputPerMTok
  *   when set, cache-write input tokens (e.g. Claude's `cache_creation_input_tokens`, typically billed at a premium) are priced at this
  *   rate instead of [[inputPerMTok]]
  */
final case class ModelPrice(
    inputPerMTok: BigDecimal,
    outputPerMTok: BigDecimal,
    cachedInputPerMTok: Option[BigDecimal] = None,
    cacheWriteInputPerMTok: Option[BigDecimal] = None
)

/** A monetary cost. The unit is whatever currency the [[PriceTable]] was written in. */
final case class Cost(value: BigDecimal) extends AnyVal {
  def >=(other: Cost): Boolean = value >= other.value
}

/** User-supplied per-model prices, keyed by the provider-reported model id (e.g. "gpt-4o-mini").
  *
  * The library ships NO prices: they change frequently and vary by provider, tier, and region.
  */
final case class PriceTable(prices: Map[String, ModelPrice]) {

  /** Total cost of the given calls. IMPORTANT: calls whose model id is absent from the table (or not reported by the provider) contribute
    * ZERO — a cost budget silently under-counts unknown models rather than failing the run. Prefer a token budget when the models in play
    * are not all priced.
    */
  def costOf(calls: Seq[LlmCallUsage]): Cost = Cost(calls.map(costOfCall).sum)

  private def costOfCall(call: LlmCallUsage): BigDecimal =
    call.model.flatMap(prices.get) match {
      case None        => BigDecimal(0)
      case Some(price) =>
        val cachedInput = BigDecimal(call.usage.cachedInputTokens.value)
        val cacheWriteInput = BigDecimal(call.usage.cacheWriteInputTokens.value)
        val plainInput = BigDecimal(
          math.max(call.usage.inputTokens.value - call.usage.cachedInputTokens.value - call.usage.cacheWriteInputTokens.value, 0L)
        )
        val output = BigDecimal(call.usage.outputTokens.value)
        (plainInput * price.inputPerMTok +
          cachedInput * price.cachedInputPerMTok.getOrElse(price.inputPerMTok) +
          cacheWriteInput * price.cacheWriteInputPerMTok.getOrElse(price.inputPerMTok) +
          output * price.outputPerMTok) / 1000000
    }
}

/** Ends the agent loop gracefully when a token or cost budget is exhausted.
  *
  * Overrides only [[AgentInterceptor.decide]]: when `state.usage.totalTokens >= maxTotalTokens` or the [[PriceTable]]-computed cost of
  * `state.llmCalls` reaches `maxCost`, the loop injects `instruction` as a user message, withholds tools, and finishes with
  * [[FinishReason.BudgetExceeded]] — mirroring the existing last-iteration behavior instead of failing abruptly.
  *
  * The cost check requires BOTH `maxCost` and `priceTable`: setting `maxCost` without a `priceTable` fails at construction with an
  * `IllegalArgumentException`, since such a budget could never trigger. See [[PriceTable.costOf]] for the unknown-model caveat.
  */
final class BudgetInterceptor[F[_]](
    maxTotalTokens: Option[Tokens] = None,
    maxCost: Option[Cost] = None,
    priceTable: Option[PriceTable] = None,
    instruction: String = BudgetInterceptor.defaultInstruction
) extends AgentInterceptor[F] {

  require(
    maxTotalTokens.nonEmpty || maxCost.nonEmpty,
    "BudgetInterceptor needs at least one limit: set maxTotalTokens and/or maxCost"
  )
  require(
    maxCost.isEmpty || priceTable.nonEmpty,
    "maxCost requires a priceTable; without one the cost budget can never trigger"
  )

  override def decide(state: AgentRunState): LoopDecision = {
    val tokensExceeded = maxTotalTokens.exists(limit => state.usage.totalTokens >= limit)
    val costExceeded = (maxCost, priceTable) match {
      case (Some(limit), Some(table)) => table.costOf(state.llmCalls) >= limit
      case _                          => false
    }
    if (tokensExceeded || costExceeded) LoopDecision.FinishNow(FinishReason.BudgetExceeded, instruction)
    else LoopDecision.Continue
  }
}

object BudgetInterceptor {

  def apply[F[_]](
      maxTotalTokens: Option[Tokens] = None,
      maxCost: Option[Cost] = None,
      priceTable: Option[PriceTable] = None,
      instruction: String = defaultInstruction
  ): BudgetInterceptor[F] = new BudgetInterceptor[F](maxTotalTokens, maxCost, priceTable, instruction)

  /** Mirrors the last-iteration rule in the default agent system prompt. */
  val defaultInstruction: String =
    "The resource budget for this task is exhausted. Provide your final answer now based on the information gathered so far, " +
      "even if the result is partial, approximate, or only a summary."
}
