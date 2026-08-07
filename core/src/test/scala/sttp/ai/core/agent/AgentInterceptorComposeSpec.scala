package sttp.ai.core.agent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.shared.Identity

class AgentInterceptorComposeSpec extends AnyFlatSpec with Matchers {

  private def state(iterations: Int = 0): AgentRunState =
    AgentRunState(iterationsCompleted = iterations, maxIterations = 10, usage = TokenUsage.Zero, llmCalls = Seq.empty)

  private class Recording(name: String, log: collection.mutable.Buffer[String]) extends AgentInterceptor[Identity] {
    override def aroundIteration[A](ctx: IterationContext)(next: => Identity[A]): Identity[A] = {
      log += s"$name:iter:before"
      val result = next
      log += s"$name:iter:after"
      result
    }
    override def aroundLlmCall(ctx: LlmCallContext)(next: => Identity[AgentResponse]): Identity[AgentResponse] = {
      log += s"$name:llm:before"
      val result = next
      log += s"$name:llm:after"
      result
    }
    override def aroundToolCall(ctx: ToolCallContext)(next: => Identity[ToolCallRecord]): Identity[ToolCallRecord] = {
      log += s"$name:tool:before"
      val result = next
      log += s"$name:tool:after"
      result
    }
  }

  "AgentInterceptor.compose" should "nest around* with the first interceptor outermost" in {
    val log = collection.mutable.Buffer.empty[String]
    val composed = AgentInterceptor.compose(Seq(new Recording("a", log), new Recording("b", log)))

    composed.aroundIteration(IterationContext(IterationInfo(1, 10))) {
      log += "body"
      42
    } shouldBe 42

    log.toList shouldBe List("a:iter:before", "b:iter:before", "body", "b:iter:after", "a:iter:after")
  }

  it should "return Continue when all interceptors continue" in {
    val log = collection.mutable.Buffer.empty[String]
    val composed = AgentInterceptor.compose(Seq(new Recording("a", log), new Recording("b", log)))
    composed.decide(state()) shouldBe LoopDecision.Continue
  }

  it should "return the first FinishNow in list order" in {
    val first = new AgentInterceptor[Identity] {
      override def decide(s: AgentRunState): LoopDecision = LoopDecision.FinishNow(FinishReason.Custom("deadline"), "first")
    }
    val second = new AgentInterceptor[Identity] {
      override def decide(s: AgentRunState): LoopDecision = LoopDecision.FinishNow(FinishReason.BudgetExceeded, "second")
    }
    val composed = AgentInterceptor.compose(Seq(AgentInterceptor.noop[Identity], first, second))
    composed.decide(state()) shouldBe LoopDecision.FinishNow(FinishReason.Custom("deadline"), "first")
  }

  it should "not evaluate next when an interceptor short-circuits" in {
    var evaluated = false
    val shortCircuit = new AgentInterceptor[Identity] {
      override def aroundIteration[A](ctx: IterationContext)(next: => Identity[A]): Identity[A] =
        throw new RuntimeException("boom")
    }
    val composed = AgentInterceptor.compose(Seq(shortCircuit))
    a[RuntimeException] should be thrownBy
      composed.aroundIteration(IterationContext(IterationInfo(1, 10))) { evaluated = true; 1 }
    evaluated shouldBe false
  }

  it should "compose an empty list into a pass-through" in {
    val composed = AgentInterceptor.compose(Seq.empty[AgentInterceptor[Identity]])
    composed.aroundIteration(IterationContext(IterationInfo(1, 10)))(7) shouldBe 7
    composed.decide(state()) shouldBe LoopDecision.Continue
  }
}
