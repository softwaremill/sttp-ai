package sttp.ai.core.agent

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.model.{AIModel, Capability}
import sttp.client4.testing.SyncBackendStub
import sttp.monad.IdentityMonad
import sttp.shared.Identity

class InputRenderingSpec extends AnyFlatSpec with Matchers {

  case object TestModel extends AIModel with Capability.ToolCalling with Capability.StructuredOutput {
    val value: String = "test-model"
  }

  class RecordingBackend extends AgentBackend[Identity] {
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
      AgentResponse("ok", Seq.empty, StopReason.EndTurn)
    }
  }

  case class CityQuery(city: String, days: Int)
  implicit val cityQueryCodec: Codec[CityQuery] = deriveCodec

  private def firstPrompt(stub: RecordingBackend): String =
    stub.receivedHistories.head.entries.collectFirst { case ConversationEntry.UserPrompt(content) => content }.get

  "input[In] (JSON default)" should "render the input as JSON inside the standard envelope" in {
    val stub = new RecordingBackend
    val agent = AgentBuilder[Identity, TestModel.type](_ => stub)(IdentityMonad).input[CityQuery].build

    agent.run(CityQuery("Paris", 3))(SyncBackendStub): Unit

    firstPrompt(stub) shouldBe
      s"""Process the following input data (JSON):
         |
         |{"city":"Paris","days":3}""".stripMargin
  }

  "String input (default)" should "render identically with no envelope" in {
    val stub = new RecordingBackend
    val agent = AgentBuilder[Identity, TestModel.type](_ => stub)(IdentityMonad).build

    agent.run("plain prompt")(SyncBackendStub): Unit

    firstPrompt(stub) shouldBe "plain prompt"
  }

  "inputRenderer" should "use the explicit rendering" in {
    val stub = new RecordingBackend
    val agent = AgentBuilder[Identity, TestModel.type](_ => stub)(IdentityMonad)
      .inputRenderer[CityQuery](q => s"Weather for ${q.city} over ${q.days} days, please.")
      .build

    agent.run(CityQuery("Paris", 3))(SyncBackendStub): Unit

    firstPrompt(stub) shouldBe "Weather for Paris over 3 days, please."
  }
}
