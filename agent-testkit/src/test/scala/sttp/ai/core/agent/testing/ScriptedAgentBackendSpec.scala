package sttp.ai.core.agent.testing

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.agent.{AgentResponse, AgentTool, ConversationHistory, IterationInfo}
import sttp.client4.testing.SyncBackendStub
import sttp.monad.IdentityMonad
import sttp.shared.Identity
import sttp.tapir.Schema

class ScriptedAgentBackendSpec extends AnyFlatSpec with Matchers {

  case class EchoInput(text: String)
  implicit val echoCodec: Codec[EchoInput] = deriveCodec
  implicit val echoSchema: Schema[EchoInput] = Schema.derived

  private val echoTool = AgentTool.fromFunction("echo", "Echoes the input")((input: EchoInput) => input.text)

  private val history = ConversationHistory.withInitialPrompt("hello")

  private def newBackend(script: AgentResponse*): ScriptedAgentBackend[Identity] =
    new ScriptedAgentBackend[Identity](script, Seq(echoTool), Some("be helpful"))(IdentityMonad)

  "ScriptedAgentBackend" should "return the scripted responses in order" in {
    val backend = newBackend(ScriptedResponse.text("first"), ScriptedResponse.text("second"))

    backend.sendRequest(history, SyncBackendStub, includeTools = true, IterationInfo(1, 10)) shouldBe ScriptedResponse.text("first")
    backend.sendRequest(history, SyncBackendStub, includeTools = true, IterationInfo(2, 10)) shouldBe ScriptedResponse.text("second")
  }

  it should "record each request with its history, includeTools flag and system prompt" in {
    val backend = newBackend(ScriptedResponse.text("a"), ScriptedResponse.text("b"))

    backend.sendRequest(history, SyncBackendStub, includeTools = true, IterationInfo(1, 10))
    backend.sendRequest(history, SyncBackendStub, includeTools = false, IterationInfo(2, 10))

    backend.requests should have size 2
    backend.requests.map(_.includeTools) shouldBe Seq(true, false)
    backend.requests.head.history shouldBe history
    backend.requests.head.systemPrompt shouldBe Some("be helpful")
  }

  it should "record offered tools with their JSON schemas when includeTools is true" in {
    val backend = newBackend(ScriptedResponse.text("a"))

    backend.sendRequest(history, SyncBackendStub, includeTools = true, IterationInfo(1, 10))

    val offered = backend.requests.head.toolsOffered
    offered.map(_.name) shouldBe Seq("echo")
    offered.head.description shouldBe "Echoes the input"
    offered.head.schema shouldBe echoTool.rawJsonSchema
  }

  it should "record no offered tools when includeTools is false" in {
    val backend = newBackend(ScriptedResponse.text("a"))

    backend.sendRequest(history, SyncBackendStub, includeTools = false, IterationInfo(1, 10))

    backend.requests.head.toolsOffered shouldBe empty
  }

  it should "fail with ScriptExhaustedException when the script runs out" in {
    val backend = newBackend(ScriptedResponse.text("only one"))

    backend.sendRequest(history, SyncBackendStub, includeTools = true, IterationInfo(1, 10))
    val exception = intercept[ScriptExhaustedException] {
      backend.sendRequest(history, SyncBackendStub, includeTools = true, IterationInfo(2, 10))
    }
    exception.getMessage should include("request 2")
    exception.getMessage should include("1 response(s)")
  }

  it should "still record the request that exhausted the script" in {
    val backend = newBackend()

    intercept[ScriptExhaustedException](backend.sendRequest(history, SyncBackendStub, includeTools = true, IterationInfo(1, 10)))

    backend.requests should have size 1
  }
}
