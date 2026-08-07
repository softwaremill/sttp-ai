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

class AgentCompositionSpec extends AnyFlatSpec with Matchers {

  case object TestModel extends AIModel with Capability.ToolCalling with Capability.StructuredOutput {
    val value: String = "test-model"
  }

  class RecordingBackend(responses: AgentResponse*) extends AgentBackend[Identity] {
    private var callCount = 0
    var receivedHistories: Seq[ConversationHistory] = Seq.empty
    override def tools: Seq[AgentTool[Identity, _]] = Seq.empty
    override def systemPrompt: Option[String] = None
    override def sendRequest(
        history: ConversationHistory,
        backend: sttp.client4.Backend[Identity],
        includeTools: Boolean,
        iterationInfo: IterationInfo
    ): Identity[AgentResponse] = {
      receivedHistories = receivedHistories :+ history
      val response = if (callCount < responses.length) responses(callCount) else responses.last
      callCount += 1
      response
    }
  }

  private val backend = SyncBackendStub

  case class Location(city: String)
  implicit val locationCodec: Codec[Location] = deriveCodec
  implicit val locationSchema: Schema[Location] = Schema.derived

  private def usage(in: Long, out: Long): TokenUsage = TokenUsage(Tokens(in), Tokens(out), Tokens.Zero, Tokens.Zero)

  private def builder(stub: RecordingBackend) = AgentBuilder[Identity, TestModel.type](_ => stub)(IdentityMonad)

  "andThen" should "feed A's typed output to B as a fresh conversation" in {
    val stubA = new RecordingBackend(
      AgentResponse("""{"city":"Paris"}""", Seq.empty, StopReason.EndTurn, usage = Some(usage(10, 5)), model = Some("m"))
    )
    val stubB = new RecordingBackend(
      AgentResponse("Paris is lovely", Seq.empty, StopReason.EndTurn, usage = Some(usage(20, 8)), model = Some("m"))
    )
    val a: Agent[Identity, String, Location] = builder(stubA).deriveResponseSchema[Location].build
    val b: Agent[Identity, Location, String] = builder(stubB).input[Location].build

    val result = a.andThen(b).run("Where should I go?")(backend)

    result.finalAnswer shouldBe Right("Paris is lovely")
    // B starts fresh: exactly one request, whose only user prompt is B's rendering of A's output
    stubB.receivedHistories should have size 1
    stubB.receivedHistories.head.entries shouldBe Seq(
      ConversationEntry.UserPrompt(
        s"""Process the following input data (JSON):
           |
           |{"city":"Paris"}""".stripMargin
      )
    )
  }

  it should "aggregate metadata across both stages" in {
    val stubA = new RecordingBackend(
      AgentResponse("""{"city":"Paris"}""", Seq.empty, StopReason.EndTurn, usage = Some(usage(10, 5)), model = Some("m"))
    )
    val stubB = new RecordingBackend(
      AgentResponse("done", Seq.empty, StopReason.EndTurn, usage = Some(usage(20, 8)), model = Some("m"))
    )
    val a = builder(stubA).deriveResponseSchema[Location].build
    val b = builder(stubB).input[Location].build

    val result = a.andThen(b).run("go")(backend)

    result.iterations shouldBe 2
    result.usage shouldBe usage(30, 13)
    result.llmCalls should have size 2
    result.finishReason shouldBe FinishReason.NaturalStop
  }

  it should "short-circuit when the first stage fails, never running the second" in {
    val stubA = new RecordingBackend(
      AgentResponse("""{"city":"Par""", Seq.empty, StopReason.MaxTokens, usage = Some(usage(10, 5)), model = Some("m"))
    )
    val stubB = new RecordingBackend(AgentResponse("unreachable", Seq.empty, StopReason.EndTurn))
    val a = builder(stubA).deriveResponseSchema[Location].build
    val b = builder(stubB).input[Location].build

    val result = a.andThen(b).run("go")(backend)

    result.finalAnswer shouldBe Left(AgentIncomplete("""{"city":"Par""", FinishReason.TokenLimit, parseError = None))
    result.finishReason shouldBe FinishReason.TokenLimit
    result.usage shouldBe usage(10, 5)
    stubB.receivedHistories shouldBe empty
  }

  "map" should "transform Right results and leave failures untouched" in {
    val ok = builder(new RecordingBackend(AgentResponse("""{"city":"Paris"}""", Seq.empty, StopReason.EndTurn)))
      .deriveResponseSchema[Location]
      .build
      .map(_.city.toUpperCase)
    ok.run("go")(backend).finalAnswer shouldBe Right("PARIS")

    val failed = builder(new RecordingBackend(AgentResponse("cut", Seq.empty, StopReason.MaxTokens)))
      .deriveResponseSchema[Location]
      .build
      .map(_.city.toUpperCase)
    failed.run("go")(backend).finalAnswer shouldBe Left(AgentIncomplete("cut", FinishReason.TokenLimit, parseError = None))
  }

  "contramap" should "adapt the input before the agent runs" in {
    val stub = new RecordingBackend(AgentResponse("ok", Seq.empty, StopReason.EndTurn))
    val agent: Agent[Identity, Int, String] = builder(stub).build.contramap((n: Int) => s"count: $n")

    agent.run(7)(backend).finalAnswer shouldBe Right("ok")
    stub.receivedHistories.head.entries shouldBe Seq(ConversationEntry.UserPrompt("count: 7"))
  }

  "andThen with mismatched types" should "not compile" in
    assertTypeError("""
      val a: Agent[Identity, String, Location] = ???
      val b: Agent[Identity, Int, String] = ???
      a.andThen(b)
    """)

  "a three-stage chain" should "compose left to right" in {
    val stubA = new RecordingBackend(AgentResponse("""{"city":"Paris"}""", Seq.empty, StopReason.EndTurn))
    val stubB = new RecordingBackend(AgentResponse("nice", Seq.empty, StopReason.EndTurn))
    val stubC = new RecordingBackend(AgentResponse("final", Seq.empty, StopReason.EndTurn))
    val a = builder(stubA).deriveResponseSchema[Location].build
    val b = builder(stubB).input[Location].build
    val c = builder(stubC).build

    val result = a.andThen(b).andThen(c).run("go")(backend)

    result.finalAnswer shouldBe Right("final")
    result.iterations shouldBe 3
  }
}
