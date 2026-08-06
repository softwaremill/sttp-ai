package sttp.ai.core.agent.interceptor

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.agent._
import sttp.shared.Identity

class BudgetInterceptorSpec extends AnyFlatSpec with Matchers {

  private def usage(in: Long, out: Long, cached: Long = 0L): TokenUsage =
    TokenUsage(Tokens(in), Tokens(out), Tokens(cached), Tokens.Zero)

  private def state(calls: LlmCallUsage*): AgentRunState = {
    val total = calls.map(_.usage).foldLeft(TokenUsage.Zero)(_ + _)
    AgentRunState(iterationsCompleted = calls.size, maxIterations = 10, usage = total, llmCalls = calls)
  }

  "BudgetInterceptor token budget" should "continue below the limit and finish at or above it" in {
    val budget = new BudgetInterceptor[Identity](maxTotalTokens = Some(Tokens(200L)))

    budget.decide(state(LlmCallUsage(Some("m"), usage(100, 50)))) shouldBe LoopDecision.Continue
    budget.decide(state(LlmCallUsage(Some("m"), usage(150, 50)))) shouldBe
      LoopDecision.FinishNow(FinishReason.BudgetExceeded, BudgetInterceptor.defaultInstruction)
  }

  it should "use a custom instruction" in {
    val budget = new BudgetInterceptor[Identity](maxTotalTokens = Some(Tokens(1L)), instruction = "STOP NOW")
    budget.decide(state(LlmCallUsage(Some("m"), usage(1, 1)))) shouldBe
      LoopDecision.FinishNow(FinishReason.BudgetExceeded, "STOP NOW")
  }

  "PriceTable" should "price uncached input, cached input, and output per million tokens" in {
    val table = PriceTable(
      Map("m" -> ModelPrice(inputPerMTok = BigDecimal(2), outputPerMTok = BigDecimal(10), cachedInputPerMTok = Some(BigDecimal("0.5"))))
    )
    // 1M input of which 400k cached, 200k output:
    // uncached 600k * 2/M = 1.2, cached 400k * 0.5/M = 0.2, output 200k * 10/M = 2.0 => 3.4
    val cost = table.costOf(Seq(LlmCallUsage(Some("m"), usage(1000000L, 200000L, cached = 400000L))))
    cost shouldBe Cost(BigDecimal("3.4"))
  }

  it should "fall back to the input rate for cached tokens when no cached rate is set" in {
    val table = PriceTable(Map("m" -> ModelPrice(inputPerMTok = BigDecimal(2), outputPerMTok = BigDecimal(10))))
    val cost = table.costOf(Seq(LlmCallUsage(Some("m"), usage(1000000L, 0L, cached = 400000L))))
    cost shouldBe Cost(BigDecimal(2)) // whole input priced at 2/M
  }

  it should "contribute zero for calls with unknown or absent model ids" in {
    val table = PriceTable(Map("known" -> ModelPrice(BigDecimal(2), BigDecimal(10))))
    table.costOf(Seq(LlmCallUsage(Some("unknown"), usage(1000000L, 1000000L)))) shouldBe Cost(BigDecimal(0))
    table.costOf(Seq(LlmCallUsage(None, usage(1000000L, 1000000L)))) shouldBe Cost(BigDecimal(0))
  }

  "BudgetInterceptor cost budget" should "finish when the priced calls reach the limit" in {
    val table = PriceTable(Map("m" -> ModelPrice(BigDecimal(2), BigDecimal(10))))
    val budget = new BudgetInterceptor[Identity](maxCost = Some(Cost(BigDecimal(3))), priceTable = Some(table))

    budget.decide(state(LlmCallUsage(Some("m"), usage(500000L, 100000L)))) shouldBe LoopDecision.Continue // 1 + 1 = 2
    budget.decide(state(LlmCallUsage(Some("m"), usage(500000L, 300000L)))) shouldBe // 1 + 3 = 4 >= 3
      LoopDecision.FinishNow(FinishReason.BudgetExceeded, BudgetInterceptor.defaultInstruction)
  }

  it should "skip the cost check when no price table is configured" in {
    val budget = new BudgetInterceptor[Identity](maxCost = Some(Cost(BigDecimal(0))))
    budget.decide(state(LlmCallUsage(Some("m"), usage(1000000L, 1000000L)))) shouldBe LoopDecision.Continue
  }
}
