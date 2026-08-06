package sttp.ai.openai.agent

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.agent.AgentTool
import sttp.ai.core.agent.ConversationHistory
import sttp.ai.core.agent.IterationInfo
import sttp.ai.openai.OpenAI
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.apispec.Schema
import sttp.client4._
import sttp.client4.testing.ResponseStub
import sttp.model.StatusCode
import sttp.monad.IdentityMonad
import sttp.shared.Identity

import java.util.concurrent.atomic.AtomicReference

class OpenAIAgentBackendSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val rawSchema =
    """{"type":"object","properties":{"title":{"type":"string"},"note":{"type":"string"}},"required":["title"]}"""

  private def testTool: AgentTool[Identity, _] = {
    val schema = parse(rawSchema).value.as[Schema](sttp.apispec.circe.schemaDecoder).value
    AgentTool.dynamic("create", "Creates a thing", schema)(_ => "ok")
  }

  private def backend(strictTools: Boolean): OpenAIAgentBackend[Identity] =
    new OpenAIAgentBackend[Identity](
      new OpenAI("test-key"),
      _ => ChatCompletionModel.GPT4oMini,
      Seq(testTool),
      None,
      None,
      strictTools
    )(IdentityMonad)

  "OpenAIAgentBackend" should "register tools as strict with normalized schemas when strictTools is true" in {
    val fn = backend(strictTools = true).convertedTools.head
    fn.strict shouldBe Some(true)
    val params = Json.fromFields(fn.parameters.get)
    params.hcursor.downField("additionalProperties").as[Boolean] shouldBe Right(false)
    params.hcursor.downField("required").as[List[String]] shouldBe Right(List("title", "note"))
    params.hcursor.downField("properties").downField("note").downField("type").as[List[String]] shouldBe Right(List("string", "null"))
  }

  it should "register tools as non-strict with the original schema when strictTools is false" in {
    val fn = backend(strictTools = false).convertedTools.head
    fn.strict shouldBe Some(false)
    val params = Json.fromFields(fn.parameters.get)
    params.hcursor.downField("additionalProperties").focus shouldBe None
    params.hcursor.downField("required").as[List[String]] shouldBe Right(List("title"))
    params.hcursor.downField("properties").downField("note").downField("type").as[String] shouldBe Right("string")
  }

  private def captureRequestBody(includeTools: Boolean): String = {
    val captured = new AtomicReference[GenericRequest[_, _]](null)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { request =>
      captured.set(request)
      ResponseStub.adjust(sttp.ai.openai.fixtures.CompletionsFixture.structuredOutputsResponse, StatusCode.Ok)
    }
    backend(strictTools = true).sendRequest(
      ConversationHistory.withInitialPrompt("hello"),
      httpStub,
      includeTools = includeTools,
      IterationInfo(1, 10)
    ): Unit
    captured.get().body match {
      case StringBody(s, _, _) => s
      case other               => fail(s"expected StringBody, got $other")
    }
  }

  it should "include tools in the request body when includeTools is true" in {
    captureRequestBody(includeTools = true) should include("\"tools\"")
  }

  it should "omit tools from the request body when includeTools is false" in {
    captureRequestBody(includeTools = false) should not include "\"tools\""
  }

  it should "resolve the model per iteration via modelForIteration" in {
    val capturedModels = new AtomicReference[Vector[String]](Vector.empty)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { request =>
      val body = request.body match {
        case StringBody(s, _, _) => s
        case other               => fail(s"expected StringBody, got $other")
      }
      val model = io.circe.parser.parse(body).toOption.flatMap(_.hcursor.get[String]("model").toOption).getOrElse("?")
      capturedModels.updateAndGet(_ :+ model)
      ResponseStub.adjust(sttp.ai.openai.fixtures.CompletionsFixture.structuredOutputsResponse, StatusCode.Ok)
    }

    val backend = new OpenAIAgentBackend[Identity](
      new OpenAI("test-key"),
      info => if (info.isLastIteration) ChatCompletionModel.GPT41 else ChatCompletionModel.GPT4oMini,
      Seq.empty,
      None,
      None,
      strictTools = true
    )(IdentityMonad)

    backend.sendRequest(ConversationHistory.withInitialPrompt("hello"), httpStub, includeTools = false, IterationInfo(1, 3)): Unit
    backend.sendRequest(ConversationHistory.withInitialPrompt("hello"), httpStub, includeTools = false, IterationInfo(3, 3)): Unit

    capturedModels.get() shouldBe Vector("gpt-4o-mini", "gpt-4.1")
  }

  it should "surface provider-reported usage and model on AgentResponse" in {
    val responseJson =
      """{
        |  "id": "chatcmpl-1",
        |  "object": "chat.completion",
        |  "created": 1,
        |  "model": "gpt-4o-mini",
        |  "choices": [
        |    {
        |      "index": 0,
        |      "message": { "role": "assistant", "content": "hi" },
        |      "finish_reason": "stop"
        |    }
        |  ],
        |  "usage": {
        |    "prompt_tokens": 100,
        |    "completion_tokens": 30,
        |    "total_tokens": 130,
        |    "prompt_tokens_details": { "cached_tokens": 40 },
        |    "completion_tokens_details": { "reasoning_tokens": 10 }
        |  }
        |}""".stripMargin
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust(responseJson, StatusCode.Ok))

    val response = backend(strictTools = true).sendRequest(
      ConversationHistory.withInitialPrompt("hello"),
      httpStub,
      includeTools = false,
      IterationInfo(1, 10)
    )

    response.model shouldBe Some("gpt-4o-mini")
    response.usage shouldBe Some(
      sttp.ai.core.agent.TokenUsage(
        inputTokens = sttp.ai.core.agent.Tokens(100L),
        outputTokens = sttp.ai.core.agent.Tokens(30L),
        cachedInputTokens = sttp.ai.core.agent.Tokens(40L),
        reasoningTokens = sttp.ai.core.agent.Tokens(10L)
      )
    )
  }
}
