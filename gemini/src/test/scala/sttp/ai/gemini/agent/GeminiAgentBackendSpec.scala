package sttp.ai.gemini.agent

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.models.Tool
import sttp.ai.core.agent._
import sttp.apispec.Schema
import sttp.client4._
import sttp.client4.testing.ResponseStub
import sttp.model.StatusCode
import sttp.monad.IdentityMonad
import sttp.shared.Identity

import java.util.concurrent.atomic.AtomicReference

class GeminiAgentBackendSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val rawSchema =
    """{"type":"object",
      |"properties":{
      |  "title":{"type":"string"},
      |  "location":{"type":"object","properties":{"lat":{"type":"number"},"lng":{"type":"number"}},"required":["lat","lng"]}
      |},
      |"required":["title","location"]}""".stripMargin

  private def newBackend(tools: Seq[AgentTool[Identity, _]]): GeminiAgentBackend[Identity] = {
    val client = GeminiClient(GeminiConfig(apiKey = "test-key"))
    new GeminiAgentBackend[Identity](client, "gemini-2.5-flash-lite", tools, None, None)(IdentityMonad)
  }

  "GeminiAgentBackend" should "pass the full tool schema through, preserving nested structure" in {
    val schema = parse(rawSchema).value.as[Schema](sttp.apispec.circe.schemaDecoder).value
    val tool = AgentTool.dynamic("create-event", "Creates an event", schema)(_ => "ok")

    newBackend(Seq(tool)).convertedTools.head match {
      case Tool.Function(name, description, parameters) =>
        name shouldBe "create-event"
        description shouldBe Some("Creates an event")
        parameters.hcursor.downField("required").as[List[String]] shouldBe Right(List("title", "location"))
      case other => fail(s"expected Tool.Function, got $other")
    }
  }

  it should "replace a boolean schema (MCP's `true` = any input) with a minimal object schema" in {
    val tool = new AgentTool[Identity, Map[String, Json]] {
      override def name: String = "any-input-tool"
      override def description: String = "Accepts any input"
      override def jsonSchema: Schema = parse("""{"type":"object"}""").value.as[Schema](sttp.apispec.circe.schemaDecoder).value
      override def codec: io.circe.Codec[Map[String, Json]] = io.circe.Codec.implied
      override def execute(input: Map[String, Json]): Identity[String] = "ok"
      override def rawJsonSchema: Json = Json.True
    }

    newBackend(Seq(tool)).convertedTools.head match {
      case Tool.Function(_, _, parameters) => parameters shouldBe parse("""{"type":"object"}""").value
      case other                           => fail(s"expected Tool.Function, got $other")
    }
  }

  private val completedResponse =
    """{
      |  "id": "int_1",
      |  "status": "completed",
      |  "steps": [{"type": "model_output", "content": [{"type": "text", "text": "done"}]}],
      |  "usage": {"total_input_tokens": 10, "total_output_tokens": 5}
      |}""".stripMargin

  private def captureRequestBody(includeTools: Boolean, history: ConversationHistory): String = {
    val schema = parse(rawSchema).value.as[Schema](sttp.apispec.circe.schemaDecoder).value
    val tool = AgentTool.dynamic("create-event", "Creates an event", schema)(_ => "ok")
    val backend = newBackend(Seq(tool))

    val captured = new AtomicReference[GenericRequest[_, _]](null)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { request =>
      captured.set(request)
      ResponseStub.adjust(completedResponse, StatusCode.Ok)
    }
    backend.sendRequest(history, httpStub, includeTools = includeTools): Unit
    captured.get().body match {
      case StringBody(s, _, _) => s
      case other               => fail(s"expected StringBody, got $other")
    }
  }

  it should "always send store=false and never previous_interaction_id (stateless replay)" in {
    val body = captureRequestBody(includeTools = true, ConversationHistory.withInitialPrompt("hello"))
    body should include("\"store\":false")
    body should not include "previous_interaction_id"
  }

  it should "replay the full history as input steps" in {
    val history = ConversationHistory.empty
      .addUserPrompt("what's the weather?")
      .addAssistantResponse("checking", Seq(ToolCall("call_1", "get_weather", """{"city":"Warsaw"}""")))
      .addToolResult(ToolCallRecord("call_1", "get_weather", """{"city":"Warsaw"}""", "sunny", 1))

    val bodyJson = parse(captureRequestBody(includeTools = true, history)).value
    val input = bodyJson.hcursor.downField("input")
    input.downN(0).downField("type").as[String] shouldBe Right("user_input")
    input.downN(1).downField("type").as[String] shouldBe Right("model_output")
    input.downN(2).downField("type").as[String] shouldBe Right("function_call")
    input.downN(2).downField("arguments").downField("city").as[String] shouldBe Right("Warsaw")
    input.downN(3).downField("type").as[String] shouldBe Right("function_result")
    input.downN(3).downField("call_id").as[String] shouldBe Right("call_1")
  }

  it should "include tools when includeTools is true and omit them when false" in {
    captureRequestBody(includeTools = true, ConversationHistory.withInitialPrompt("hi")) should include("\"tools\"")
    captureRequestBody(includeTools = false, ConversationHistory.withInitialPrompt("hi")) should not include "\"tools\""
  }

  it should "map a completed response to EndTurn with the model output text" in {
    val backend = newBackend(Seq.empty)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust(completedResponse, StatusCode.Ok))

    val response = backend.sendRequest(ConversationHistory.withInitialPrompt("hi"), httpStub, includeTools = false)
    response.textContent shouldBe "done"
    response.toolCalls shouldBe empty
    response.stopReason shouldBe StopReason.EndTurn
  }

  it should "map function calls to ToolUse with ToolCall entries" in {
    val toolCallResponse =
      """{
        |  "id": "int_2",
        |  "status": "requires_action",
        |  "steps": [{"type": "function_call", "id": "call_9", "name": "get_weather", "arguments": {"city": "Warsaw"}}]
        |}""".stripMargin
    val backend = newBackend(Seq.empty)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust(toolCallResponse, StatusCode.Ok))

    val response = backend.sendRequest(ConversationHistory.withInitialPrompt("hi"), httpStub, includeTools = false)
    response.stopReason shouldBe StopReason.ToolUse
    response.toolCalls shouldBe Seq(ToolCall("call_9", "get_weather", """{"city":"Warsaw"}"""))
  }
}
