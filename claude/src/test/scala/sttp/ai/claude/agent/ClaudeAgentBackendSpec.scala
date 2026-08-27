package sttp.ai.claude.agent

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.ClaudeModel
import sttp.ai.claude.models.Tool
import sttp.ai.core.agent.AgentTool
import sttp.ai.core.agent.ConversationHistory
import sttp.ai.core.agent.IterationInfo
import sttp.apispec.Schema
import sttp.client4._
import sttp.client4.testing.ResponseStub
import sttp.model.StatusCode
import sttp.monad.IdentityMonad
import sttp.shared.Identity

import java.util.concurrent.atomic.AtomicReference

class ClaudeAgentBackendSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val rawSchema =
    """{"type":"object",
      |"properties":{
      |  "title":{"type":"string"},
      |  "location":{"type":"object","properties":{"lat":{"type":"number"},"lng":{"type":"number"}},"required":["lat","lng"]}
      |},
      |"required":["title","location"]}""".stripMargin

  "ClaudeAgentBackend" should "pass the full tool schema through, preserving nested structure" in {
    val schema = parse(rawSchema).value.as[Schema](sttp.apispec.circe.schemaDecoder).value
    val tool = AgentTool.dynamic("create-event", "Creates an event", schema)(_ => "ok")

    val client = ClaudeClient(ClaudeConfig(apiKey = "test-key"))
    val backend = new ClaudeAgentBackend[Identity](client, _ => ClaudeModel.ClaudeHaiku4_5, Seq(tool), None, None)(IdentityMonad)

    backend.convertedTools.head match {
      case raw: Tool.CustomRaw =>
        raw.name shouldBe "create-event"
        raw.description shouldBe "Creates an event"
        val nested = raw.inputSchema.hcursor.downField("properties").downField("location")
        nested.downField("type").as[String] shouldBe Right("object")
        nested.downField("properties").downField("lat").downField("type").as[String] shouldBe Right("number")
        raw.inputSchema.hcursor.downField("required").as[List[String]] shouldBe Right(List("title", "location"))
      case other => fail(s"expected Tool.CustomRaw, got $other")
    }
  }

  it should "default an empty schema's input_schema to type object (Anthropic requires input_schema.type)" in {
    val schema = parse("{}").value.as[Schema](sttp.apispec.circe.schemaDecoder).value
    val tool = AgentTool.dynamic("no-arg-tool", "Takes no arguments", schema)(_ => "ok")

    val client = ClaudeClient(ClaudeConfig(apiKey = "test-key"))
    val backend = new ClaudeAgentBackend[Identity](client, _ => ClaudeModel.ClaudeHaiku4_5, Seq(tool), None, None)(IdentityMonad)

    backend.convertedTools.head match {
      case raw: Tool.CustomRaw =>
        raw.inputSchema shouldBe parse("""{"type":"object","properties":{}}""").value
      case other => fail(s"expected Tool.CustomRaw, got $other")
    }
  }

  it should "pass an overridden rawJsonSchema through byte-identical, including null-valued fields" in {
    val originalSchema = parse(
      """{"type":"object","properties":{"level":{"enum":["low","high",null],"default":null}}}"""
    ).value

    val tool = new AgentTool[Identity, Map[String, Json]] {
      override def name: String = "raw-passthrough-tool"
      override def description: String = "Uses a raw schema with null-valued fields"
      override def jsonSchema: Schema = parse("""{"type":"object"}""").value.as[Schema](sttp.apispec.circe.schemaDecoder).value
      override def codec: io.circe.Codec[Map[String, Json]] = io.circe.Codec.implied
      override def execute(input: Map[String, Json]): Identity[String] = "ok"
      override def rawJsonSchema: Json = originalSchema
    }

    val client = ClaudeClient(ClaudeConfig(apiKey = "test-key"))
    val backend = new ClaudeAgentBackend[Identity](client, _ => ClaudeModel.ClaudeHaiku4_5, Seq(tool), None, None)(IdentityMonad)

    backend.convertedTools.head match {
      case raw: Tool.CustomRaw => raw.inputSchema shouldBe originalSchema
      case other               => fail(s"expected Tool.CustomRaw, got $other")
    }
  }

  it should "replace a JSON Schema boolean schema (MCP's `true` = \"any input\") with a minimal object schema" in {
    val tool = new AgentTool[Identity, Map[String, Json]] {
      override def name: String = "any-input-tool"
      override def description: String = "Accepts any input"
      override def jsonSchema: Schema = parse("""{"type":"object"}""").value.as[Schema](sttp.apispec.circe.schemaDecoder).value
      override def codec: io.circe.Codec[Map[String, Json]] = io.circe.Codec.implied
      override def execute(input: Map[String, Json]): Identity[String] = "ok"
      override def rawJsonSchema: Json = Json.True
    }

    val client = ClaudeClient(ClaudeConfig(apiKey = "test-key"))
    val backend = new ClaudeAgentBackend[Identity](client, _ => ClaudeModel.ClaudeHaiku4_5, Seq(tool), None, None)(IdentityMonad)

    backend.convertedTools.head match {
      case raw: Tool.CustomRaw => raw.inputSchema shouldBe parse("""{"type":"object","properties":{}}""").value
      case other               => fail(s"expected Tool.CustomRaw, got $other")
    }
  }

  private val minimalMessageResponse =
    """{
      |  "id": "msg_1",
      |  "type": "message",
      |  "role": "assistant",
      |  "content": [{"type": "text", "text": "done"}],
      |  "model": "claude-haiku-4-5-20251001",
      |  "stop_reason": "end_turn",
      |  "usage": {"input_tokens": 10, "output_tokens": 20}
      |}""".stripMargin

  private def captureRequestBody(includeTools: Boolean, maxTokens: Option[Int] = None): String = {
    val schema = parse(rawSchema).value.as[Schema](sttp.apispec.circe.schemaDecoder).value
    val tool = AgentTool.dynamic("create-event", "Creates an event", schema)(_ => "ok")
    val client = ClaudeClient(ClaudeConfig(apiKey = "test-key"))
    val backend = new ClaudeAgentBackend[Identity](client, _ => ClaudeModel.ClaudeHaiku4_5, Seq(tool), None, None, maxTokens)(IdentityMonad)

    val captured = new AtomicReference[GenericRequest[_, _]](null)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { request =>
      captured.set(request)
      ResponseStub.adjust(minimalMessageResponse, StatusCode.Ok)
    }
    backend.sendRequest(ConversationHistory.withInitialPrompt("hello"), httpStub, includeTools = includeTools, IterationInfo(1, 10)): Unit
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

  it should "send max_tokens 4096 by default" in {
    val bodyJson = parse(captureRequestBody(includeTools = true)).value
    bodyJson.hcursor.downField("max_tokens").as[Int] shouldBe Right(4096)
  }

  it should "send the configured max_tokens" in {
    val bodyJson = parse(captureRequestBody(includeTools = true, maxTokens = Some(8192))).value
    bodyJson.hcursor.downField("max_tokens").as[Int] shouldBe Right(8192)
  }

  it should "resolve the model per iteration via modelForIteration" in {
    val capturedModels = new AtomicReference[Vector[String]](Vector.empty)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { request =>
      val body = request.body match {
        case StringBody(s, _, _) => s
        case other               => fail(s"expected StringBody, got $other")
      }
      val model = parse(body).toOption.flatMap(_.hcursor.get[String]("model").toOption).getOrElse("?")
      capturedModels.updateAndGet(_ :+ model)
      ResponseStub.adjust(minimalMessageResponse, StatusCode.Ok)
    }

    val client = ClaudeClient(ClaudeConfig(apiKey = "test-key"))
    val backend = new ClaudeAgentBackend[Identity](
      client,
      info => if (info.isLastIteration) ClaudeModel.ClaudeOpus5 else ClaudeModel.ClaudeHaiku4_5,
      Seq.empty,
      None,
      None
    )(IdentityMonad)

    backend.sendRequest(ConversationHistory.withInitialPrompt("hello"), httpStub, includeTools = false, IterationInfo(1, 3)): Unit
    backend.sendRequest(ConversationHistory.withInitialPrompt("hello"), httpStub, includeTools = false, IterationInfo(3, 3)): Unit

    capturedModels.get() shouldBe Vector("claude-haiku-4-5-20251001", "claude-opus-5")
  }

  it should "surface provider-reported usage and model on AgentResponse" in {
    val responseJson =
      """{
        |  "id": "msg_1",
        |  "type": "message",
        |  "role": "assistant",
        |  "content": [ { "type": "text", "text": "hi" } ],
        |  "model": "claude-haiku-4-5",
        |  "stop_reason": "end_turn",
        |  "usage": {
        |    "input_tokens": 100,
        |    "output_tokens": 30,
        |    "cache_read_input_tokens": 40,
        |    "cache_creation_input_tokens": 7
        |  }
        |}""".stripMargin
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust(responseJson, StatusCode.Ok))

    val client = ClaudeClient(ClaudeConfig(apiKey = "test-key"))
    val backend = new ClaudeAgentBackend[Identity](client, _ => ClaudeModel.ClaudeHaiku4_5, Seq.empty, None, None)(IdentityMonad)

    val response = backend.sendRequest(
      ConversationHistory.withInitialPrompt("hello"),
      httpStub,
      includeTools = false,
      IterationInfo(1, 10)
    )

    response.model shouldBe Some("claude-haiku-4-5")
    response.usage shouldBe Some(
      sttp.ai.core.agent.TokenUsage(
        inputTokens = sttp.ai.core.agent.Tokens(147L), // 100 + 40 cache-read + 7 cache-creation
        outputTokens = sttp.ai.core.agent.Tokens(30L),
        cachedInputTokens = sttp.ai.core.agent.Tokens(40L),
        reasoningTokens = sttp.ai.core.agent.Tokens.Zero,
        cacheWriteInputTokens = sttp.ai.core.agent.Tokens(7L)
      )
    )
  }
}
