package sttp.ai.core.agent.interceptor

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.agent._
import sttp.shared.Identity

class LoggingInterceptorSpec extends AnyFlatSpec with Matchers {

  private def newLogger(log: collection.mutable.Buffer[(LogLevel, String)]): LoggingInterceptor[Identity] =
    new LoggingInterceptor[Identity]((level, msg) => { log += ((level, msg)); () })(sttp.monad.IdentityMonad)

  "LoggingInterceptor" should "log iteration start and end at Debug" in {
    val log = collection.mutable.Buffer.empty[(LogLevel, String)]
    newLogger(log).aroundIteration(IterationContext(IterationInfo(2, 5)))(42) shouldBe 42

    log.map(_._1).toList shouldBe List(LogLevel.Debug, LogLevel.Debug)
    log(0)._2 should include("2/5")
    log(0)._2 should include("started")
    log(1)._2 should include("finished")
  }

  it should "log LLM call completion with model and usage at Info" in {
    val log = collection.mutable.Buffer.empty[(LogLevel, String)]
    val response = AgentResponse(
      "hi",
      Seq.empty,
      StopReason.EndTurn,
      usage = Some(TokenUsage(Tokens(100L), Tokens(30L), Tokens.Zero, Tokens.Zero)),
      model = Some("test-model")
    )
    val ctx = LlmCallContext(ConversationHistory.empty, includeTools = true, IterationInfo(1, 5))
    newLogger(log).aroundLlmCall(ctx)(response) shouldBe response

    log should have size 1
    log(0)._1 shouldBe LogLevel.Info
    log(0)._2 should include("test-model")
    log(0)._2 should include("100")
    log(0)._2 should include("30")
  }

  it should "log tool call start and finish at Info" in {
    val log = collection.mutable.Buffer.empty[(LogLevel, String)]
    val record = ToolCallRecord("id1", "calculator", "{}", "42", 1)
    val ctx = ToolCallContext(ToolCall("id1", "calculator", "{}"), 1)
    newLogger(log).aroundToolCall(ctx)(record) shouldBe record

    log.map(_._1).toList shouldBe List(LogLevel.Info, LogLevel.Info)
    log(0)._2 should include("calculator")
    log(1)._2 should include("calculator")
  }
}
