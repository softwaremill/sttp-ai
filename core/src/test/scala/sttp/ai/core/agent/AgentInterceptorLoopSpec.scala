package sttp.ai.core.agent

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.model.{AIModel, Capability}
import sttp.client4.testing.SyncBackendStub
import sttp.monad.IdentityMonad
import sttp.shared.Identity
import sttp.tapir.Schema

class AgentInterceptorLoopSpec extends AnyFlatSpec with Matchers {

  case object TestModel extends AIModel with Capability.ToolCalling with Capability.StructuredOutput {
    val value: String = "test-model"
  }

  class StubAgentBackend(responses: Seq[AgentResponse]) extends AgentBackend[Identity] {
    private var callCount = 0
    var receivedHistories: Seq[ConversationHistory] = Seq.empty
    var receivedIncludeTools: Seq[Boolean] = Seq.empty
    var receivedIterationInfos: Seq[IterationInfo] = Seq.empty

    override def tools: Seq[AgentTool[Identity, _]] = Seq.empty
    override def systemPrompt: Option[String] = None

    override def sendRequest(
        history: ConversationHistory,
        backend: sttp.client4.Backend[Identity],
        includeTools: Boolean,
        iterationInfo: IterationInfo
    ): Identity[AgentResponse] = {
      receivedHistories = receivedHistories :+ history
      receivedIncludeTools = receivedIncludeTools :+ includeTools
      receivedIterationInfos = receivedIterationInfos :+ iterationInfo
      val response = if (callCount < responses.length) responses(callCount) else responses.last
      callCount += 1
      response
    }
  }

  private val backend = SyncBackendStub

  case class DummyInput()
  implicit val dummyInputCodec: Codec[DummyInput] = deriveCodec
  implicit val dummyInputSchema: Schema[DummyInput] = Schema.derived

  private val dummyTool = AgentTool.fromFunction("dummy", "Dummy tool")((_: DummyInput) => "dummy result")

  private def usage(in: Long, out: Long): TokenUsage =
    TokenUsage(Tokens(in), Tokens(out), Tokens.Zero, Tokens.Zero)

  private def toolResponse(id: String, u: Option[TokenUsage], model: Option[String] = Some("test-model")): AgentResponse =
    AgentResponse("", Seq(ToolCall(id, "dummy", "{}")), StopReason.ToolUse, usage = u, model = model)

  private def finalResponse(text: String, u: Option[TokenUsage]): AgentResponse =
    AgentResponse(text, Seq.empty, StopReason.EndTurn, usage = u, model = Some("test-model"))

  private def build(stub: StubAgentBackend, interceptors: Seq[AgentInterceptor[Identity]]): Agent[Identity, String, String] =
    AgentBuilder[Identity, TestModel.type](_ => stub)(IdentityMonad)
      .maxIterations(5)
      .tools(dummyTool)
      .interceptors(interceptors)
      .build

  "Agent with interceptors" should "accumulate usage and per-call breakdown into AgentResult" in {
    val stub = new StubAgentBackend(
      Seq(
        toolResponse("call_1", Some(usage(100, 20))),
        toolResponse("call_2", None), // a call with no reported usage contributes Zero
        finalResponse("done", Some(usage(50, 10)))
      )
    )
    val result = build(stub, Seq.empty).run("Test")(backend)

    result.finalAnswer shouldBe Right("done")
    result.usage shouldBe usage(150, 30)
    result.llmCalls shouldBe Seq(
      LlmCallUsage(Some("test-model"), usage(100, 20)),
      LlmCallUsage(Some("test-model"), TokenUsage.Zero),
      LlmCallUsage(Some("test-model"), usage(50, 10))
    )
  }

  it should "invoke around stages in onion order for each stage kind" in {
    val log = collection.mutable.Buffer.empty[String]
    class Rec(name: String) extends AgentInterceptor[Identity] {
      override def aroundIteration[A](ctx: IterationContext)(next: => Identity[A]): Identity[A] = {
        log += s"$name:iter(${ctx.iterationInfo.iteration}):in"; val r = next; log += s"$name:iter(${ctx.iterationInfo.iteration}):out"; r
      }
      override def aroundLlmCall(ctx: LlmCallContext)(next: => Identity[AgentResponse]): Identity[AgentResponse] = {
        log += s"$name:llm:in"; val r = next; log += s"$name:llm:out"; r
      }
      override def aroundToolCall(ctx: ToolCallContext)(next: => Identity[ToolCallRecord]): Identity[ToolCallRecord] = {
        log += s"$name:tool(${ctx.toolCall.toolName}):in"; val r = next; log += s"$name:tool(${ctx.toolCall.toolName}):out"; r
      }
    }
    val stub = new StubAgentBackend(
      Seq(toolResponse("call_1", Some(usage(1, 1))), finalResponse("done", Some(usage(1, 1))))
    )
    build(stub, Seq(new Rec("a"), new Rec("b"))).run("Test")(backend).finalAnswer shouldBe Right("done")

    log.toList shouldBe List(
      "a:iter(1):in",
      "b:iter(1):in",
      "a:llm:in",
      "b:llm:in",
      "b:llm:out",
      "a:llm:out",
      "a:tool(dummy):in",
      "b:tool(dummy):in",
      "b:tool(dummy):out",
      "a:tool(dummy):out",
      "b:iter(1):out",
      "a:iter(1):out",
      "a:iter(2):in",
      "b:iter(2):in",
      "a:llm:in",
      "b:llm:in",
      "b:llm:out",
      "a:llm:out",
      "b:iter(2):out",
      "a:iter(2):out"
    )
  }

  it should "fail the run when an interceptor throws" in {
    val boom = new AgentInterceptor[Identity] {
      override def aroundLlmCall(ctx: LlmCallContext)(next: => Identity[AgentResponse]): Identity[AgentResponse] =
        throw new IllegalStateException("interceptor failure")
    }
    val stub = new StubAgentBackend(Seq(finalResponse("done", None)))
    an[IllegalStateException] should be thrownBy build(stub, Seq(boom)).run("Test")(backend)
  }

  it should "let an interceptor short-circuit the LLM call by not evaluating next" in {
    val cached = finalResponse("cached answer", Some(usage(1, 1)))
    val shortCircuit = new AgentInterceptor[Identity] {
      override def aroundLlmCall(ctx: LlmCallContext)(next: => Identity[AgentResponse]): Identity[AgentResponse] = cached
    }
    val stub = new StubAgentBackend(Seq(finalResponse("real answer", None)))
    val result = build(stub, Seq(shortCircuit)).run("Test")(backend)

    result.finalAnswer shouldBe Right("cached answer")
    result.usage shouldBe usage(1, 1)
    stub.receivedHistories shouldBe empty // the backend was never reached
  }

  private def finishAfter(calls: Int, cause: FinishReason.ForcedStop, instruction: String): AgentInterceptor[Identity] =
    new AgentInterceptor[Identity] {
      override def decide(state: AgentRunState): LoopDecision =
        if (state.llmCalls.size >= calls) LoopDecision.FinishNow(cause, instruction) else LoopDecision.Continue
    }

  it should "force a graceful final answer when decide returns FinishNow" in {
    val stub = new StubAgentBackend(
      Seq(
        toolResponse("call_1", Some(usage(100, 20))),
        finalResponse("budget answer", Some(usage(50, 10)))
      )
    )
    val steer = finishAfter(1, FinishReason.BudgetExceeded, "BUDGET EXHAUSTED - answer now")
    val result = build(stub, Seq(steer)).run("Test")(backend)

    result.finishReason shouldBe FinishReason.BudgetExceeded
    result.finalAnswer shouldBe Right("budget answer")
    result.iterations shouldBe 2
    // The forced-final request withheld tools and contained the injected instruction:
    stub.receivedIncludeTools shouldBe Seq(true, false)
    stub.receivedHistories.last.entries should contain(ConversationEntry.UserPrompt("BUDGET EXHAUSTED - answer now"))
    // No iteration marker on the forced-final request — it would contradict the injected instruction:
    stub.receivedHistories.last.entries.collect { case m: ConversationEntry.IterationMarker => m } shouldBe empty
    // The backend sees the forced final via IterationInfo, so modelForIteration can pick the stronger model:
    stub.receivedIterationInfos.map(_.isLastIteration) shouldBe Seq(false, true)
    // Usage includes the forced-final call:
    result.usage shouldBe usage(150, 30)
  }

  it should "report MaxIterations when FinishNow coincides with the forced last iteration" in {
    val stub = new StubAgentBackend(
      Seq(
        toolResponse("call_1", Some(usage(10, 5))),
        finalResponse("last answer", Some(usage(10, 5)))
      )
    )
    val steer = finishAfter(1, FinishReason.BudgetExceeded, "answer now")
    val agent = AgentBuilder[Identity, TestModel.type](_ => stub)(IdentityMonad)
      .maxIterations(2) // iteration 2 is the forced last iteration AND the FinishNow iteration
      .tools(dummyTool)
      .interceptors(Seq(steer))
      .build

    val result = agent.run("Test")(backend)
    result.finishReason shouldBe FinishReason.MaxIterations
    result.finalAnswer shouldBe Right("last answer")
  }

  it should "not consult tools nor execute spurious tool calls on a FinishNow iteration" in {
    val stub = new StubAgentBackend(
      Seq(
        toolResponse("call_1", Some(usage(10, 5))),
        // model misbehaves and still asks for a tool on the forced-final call; it must not be executed
        toolResponse("call_2", Some(usage(10, 5)))
      )
    )
    val steer = finishAfter(1, FinishReason.BudgetExceeded, "answer now")
    val result = build(stub, Seq(steer)).run("Test")(backend)

    result.finishReason shouldBe FinishReason.BudgetExceeded
    result.toolCalls should have size 1 // only the first iteration's tool call
    result.finalAnswer shouldBe Right("dummy result") // extractFinalAnswer fallback: last tool result
  }

  it should "enforce a token budget end-to-end via BudgetInterceptor" in {
    import sttp.ai.core.agent.interceptor.BudgetInterceptor
    val stub = new StubAgentBackend(
      Seq(
        toolResponse("call_1", Some(usage(100, 20))),
        toolResponse("call_2", Some(usage(100, 20))),
        finalResponse("partial answer", Some(usage(50, 10)))
      )
    )
    val budget = new BudgetInterceptor[Identity](maxTotalTokens = Some(Tokens(200L)))
    val result = build(stub, Seq(budget)).run("Test")(backend)

    result.finishReason shouldBe FinishReason.BudgetExceeded
    result.finalAnswer shouldBe Right("partial answer")
    result.iterations shouldBe 3 // breach detected after call 2 (240 >= 200), forced final on iteration 3
    stub.receivedIncludeTools shouldBe Seq(true, true, false)
    stub.receivedHistories.last.entries should contain(ConversationEntry.UserPrompt(BudgetInterceptor.defaultInstruction))
  }

  it should "force a graceful final answer via FinishNow before the first LLM call" in {
    val stub = new StubAgentBackend(Seq(finalResponse("immediate answer", Some(usage(10, 5)))))
    val steer = finishAfter(0, FinishReason.BudgetExceeded, "answer immediately")
    val result = build(stub, Seq(steer)).run("Test")(backend)

    result.finishReason shouldBe FinishReason.BudgetExceeded
    result.finalAnswer shouldBe Right("immediate answer")
    result.iterations shouldBe 1
    stub.receivedIncludeTools shouldBe Seq(false)
    stub.receivedHistories.last.entries should contain(ConversationEntry.UserPrompt("answer immediately"))
  }

  it should "parse a budget-forced final answer like a max-iterations one" in {
    import sttp.ai.core.agent.interceptor.BudgetInterceptor
    case class Out(x: Int)
    implicit val outCodec: Codec[Out] = deriveCodec
    implicit val outSchema: Schema[Out] = Schema.derived

    val stub = new StubAgentBackend(
      Seq(
        toolResponse("call_1", Some(usage(100, 20))),
        finalResponse("""{"x": 5}""", Some(usage(50, 10)))
      )
    )
    val budget = new BudgetInterceptor[Identity](maxTotalTokens = Some(Tokens(100L)))
    val result = AgentBuilder[Identity, TestModel.type](_ => stub)(IdentityMonad)
      .maxIterations(5)
      .tools(dummyTool)
      .interceptors(Seq(budget))
      .deriveResponseSchema[Out]
      .build
      .run("Test")(backend)

    result.finishReason shouldBe FinishReason.BudgetExceeded
    result.finalAnswer shouldBe Right(Out(5)) // BudgetExceeded is parse-attempted, not rejected outright
    result.usage shouldBe usage(150, 30)
  }

  it should "wrap stages and accumulate usage under a lazy effect type (Future)" in {
    import scala.concurrent.duration.DurationInt
    import scala.concurrent.{Await, ExecutionContext, Future}
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val futureMonad: sttp.monad.MonadError[Future] = new sttp.monad.FutureMonad()

    class FutureStubBackend(responses: Seq[AgentResponse]) extends AgentBackend[Future] {
      private var callCount = 0
      override def tools: Seq[AgentTool[Future, _]] = Seq.empty
      override def systemPrompt: Option[String] = None
      override def sendRequest(
          history: ConversationHistory,
          backend: sttp.client4.Backend[Future],
          includeTools: Boolean,
          iterationInfo: IterationInfo
      ): Future[AgentResponse] = {
        val response = if (callCount < responses.length) responses(callCount) else responses.last
        callCount += 1
        Future.successful(response)
      }
    }

    val log = collection.mutable.Buffer.empty[String]
    val recording = new AgentInterceptor[Future] {
      override def aroundLlmCall(ctx: LlmCallContext)(next: => Future[AgentResponse]): Future[AgentResponse] = {
        log += "llm:in"
        next.map { r => log += "llm:out"; r }
      }
      override def aroundToolCall(ctx: ToolCallContext)(next: => Future[ToolCallRecord]): Future[ToolCallRecord] = {
        log += "tool:in"
        next.map { r => log += "tool:out"; r }
      }
    }

    val futureTool = AgentTool.fromFunctionF[Future, DummyInput]("dummy", "Dummy tool")(_ => Future.successful("dummy result"))
    val stub = new FutureStubBackend(
      Seq(
        toolResponse("call_1", Some(usage(100, 20))),
        finalResponse("done", Some(usage(50, 10)))
      )
    )
    val agent = AgentBuilder[Future, TestModel.type](_ => stub)
      .maxIterations(5)
      .tools(futureTool)
      .interceptors(Seq(recording))
      .build

    val result = Await.result(agent.run("Test")(sttp.client4.testing.BackendStub[Future](futureMonad)), 10.seconds)

    result.finalAnswer shouldBe Right("done")
    result.usage shouldBe usage(150, 30)
    log.toList shouldBe List("llm:in", "llm:out", "tool:in", "tool:out", "llm:in", "llm:out")
  }
}
