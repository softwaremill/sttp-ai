package sttp.ai.gemini.agent

import io.circe.Json
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.parse
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.models.{GeminiModel, Tool}
import sttp.ai.core.agent._
import sttp.apispec.Schema
import sttp.client4._
import sttp.client4.testing.ResponseStub
import sttp.model.StatusCode
import sttp.monad.IdentityMonad
import sttp.shared.Identity
import sttp.tapir.{Schema => TapirSchema}

import java.util.concurrent.atomic.AtomicReference

case class GeminiAgentWeatherSummary(city: String, tempC: Double)

object GeminiAgentWeatherSummary {
  implicit val codec: io.circe.Codec[GeminiAgentWeatherSummary] = deriveCodec
  implicit val schema: TapirSchema[GeminiAgentWeatherSummary] = TapirSchema.derived
}

class GeminiAgentBackendSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val testModel = GeminiModel.Gemini35FlashLite.value

  private val rawSchema =
    """{"type":"object",
      |"properties":{
      |  "title":{"type":"string"},
      |  "location":{"type":"object","properties":{"lat":{"type":"number"},"lng":{"type":"number"}},"required":["lat","lng"]}
      |},
      |"required":["title","location"]}""".stripMargin

  private def newBackend(
      tools: Seq[AgentTool[Identity, _]],
      systemPrompt: Option[String] = None,
      responseSchema: Option[ResponseSchema[_]] = None
  ): GeminiAgentBackend[Identity] = {
    val client = GeminiClient(GeminiConfig(apiKey = "test-key"))
    new GeminiAgentBackend[Identity](client, _ => GeminiModel.CustomModel(testModel), tools, systemPrompt, responseSchema)(
      IdentityMonad
    )
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
      case Tool.Function(_, _, parameters) => parameters shouldBe parse("""{"type":"object","properties":{}}""").value
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

  private def captureRequestBody(
      includeTools: Boolean,
      history: ConversationHistory,
      systemPrompt: Option[String] = None,
      responseSchema: Option[ResponseSchema[_]] = None
  ): String = {
    val schema = parse(rawSchema).value.as[Schema](sttp.apispec.circe.schemaDecoder).value
    val tool = AgentTool.dynamic("create-event", "Creates an event", schema)(_ => "ok")
    val backend = newBackend(Seq(tool), systemPrompt, responseSchema)

    val captured = new AtomicReference[GenericRequest[_, _]](null)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { request =>
      captured.set(request)
      ResponseStub.adjust(completedResponse, StatusCode.Ok)
    }
    backend.sendRequest(history, httpStub, includeTools = includeTools, IterationInfo(1, 10)): Unit
    captured.get().body match {
      case StringBody(s, _, _) => s
      case other               => fail(s"expected StringBody, got $other")
    }
  }

  it should "always send store=false and never previous_interaction_id (stateless replay)" in {
    val bodyJson = parse(captureRequestBody(includeTools = true, ConversationHistory.withInitialPrompt("hello"))).value
    bodyJson.hcursor.downField("store").as[Boolean] shouldBe Right(false)
    bodyJson.hcursor.downField("previous_interaction_id").succeeded shouldBe false
  }

  it should "bound the response with a default generation_config.max_output_tokens of 4096" in {
    val bodyJson = parse(captureRequestBody(includeTools = true, ConversationHistory.withInitialPrompt("hello"))).value
    bodyJson.hcursor.downField("generation_config").downField("max_output_tokens").as[Int] shouldBe Right(4096)
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
    val withTools = parse(captureRequestBody(includeTools = true, ConversationHistory.withInitialPrompt("hi"))).value
    withTools.hcursor.downField("tools").succeeded shouldBe true

    val withoutTools = parse(captureRequestBody(includeTools = false, ConversationHistory.withInitialPrompt("hi"))).value
    withoutTools.hcursor.downField("tools").succeeded shouldBe false
  }

  it should "map a completed response to EndTurn with the model output text" in {
    val backend = newBackend(Seq.empty)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust(completedResponse, StatusCode.Ok))

    val response = backend.sendRequest(ConversationHistory.withInitialPrompt("hi"), httpStub, includeTools = false, IterationInfo(1, 10))
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

    val response = backend.sendRequest(ConversationHistory.withInitialPrompt("hi"), httpStub, includeTools = false, IterationInfo(1, 10))
    response.stopReason shouldBe StopReason.ToolUse
    response.toolCalls shouldBe Seq(ToolCall("call_9", "get_weather", """{"city":"Warsaw"}"""))
  }

  it should "raise an error when the interaction status is failed" in {
    val failedResponse = """{"id":"int_3","status":"failed"}"""
    val backend = newBackend(Seq.empty)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust(failedResponse, StatusCode.Ok))

    a[RuntimeException] should be thrownBy
      backend.sendRequest(ConversationHistory.withInitialPrompt("hi"), httpStub, includeTools = false, IterationInfo(1, 10))
  }

  it should "map completed responses that still contain function calls to ToolUse" in {
    val completedWithCalls =
      """{"id":"int_4","status":"completed",
        |"steps":[{"type":"model_output","content":[{"type":"text","text":"calling"}]},
        |{"type":"function_call","id":"call_5","name":"get_weather","arguments":{"city":"Krakow"}}]}""".stripMargin
    val backend = newBackend(Seq.empty)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust(completedWithCalls, StatusCode.Ok))

    val response = backend.sendRequest(ConversationHistory.withInitialPrompt("hi"), httpStub, includeTools = false, IterationInfo(1, 10))
    response.stopReason shouldBe StopReason.ToolUse
    response.toolCalls.map(_.toolName) shouldBe Seq("get_weather")
  }

  it should "raise an error when the interaction status is cancelled, mentioning the status in the message" in {
    val cancelledResponse = s"""{"id":"int_6","status":"cancelled","model":"$testModel"}"""
    val backend = newBackend(Seq.empty)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust(cancelledResponse, StatusCode.Ok))

    val ex = the[RuntimeException] thrownBy
      backend.sendRequest(ConversationHistory.withInitialPrompt("hi"), httpStub, includeTools = false, IterationInfo(1, 10))
    ex.getMessage should include("cancelled")
  }

  it should "normalize a null function_call arguments field to an empty-object ToolCall input" in {
    val nullArgsResponse =
      """{"id":"int_7","status":"requires_action",
        |"steps":[{"type":"function_call","id":"call_9","name":"now","arguments":null}]}""".stripMargin
    val backend = newBackend(Seq.empty)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust(nullArgsResponse, StatusCode.Ok))

    val response = backend.sendRequest(ConversationHistory.withInitialPrompt("hi"), httpStub, includeTools = false, IterationInfo(1, 10))
    response.toolCalls shouldBe Seq(ToolCall("call_9", "now", "{}"))
  }

  it should "fail through the effect's error channel, not by throwing eagerly outside it, when replaying a malformed tool-call input" in {
    val history = ConversationHistory.empty.addAssistantResponse("", Seq(ToolCall("c1", "t", "not-json")))
    val backend = newBackend(Seq.empty)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust(completedResponse, StatusCode.Ok))

    a[io.circe.ParsingFailure] should be thrownBy backend.sendRequest(history, httpStub, includeTools = false, IterationInfo(1, 10))
  }

  it should "surface the typed GeminiException instead of wrapping it in a generic RuntimeException" in {
    val errorBody = """{"error":{"code":429,"message":"quota exceeded","status":"RESOURCE_EXHAUSTED"}}"""
    val backend = newBackend(Seq.empty)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust(errorBody, StatusCode.TooManyRequests))

    an[GeminiException.RateLimitException] should be thrownBy
      backend.sendRequest(ConversationHistory.withInitialPrompt("hi"), httpStub, includeTools = false, IterationInfo(1, 10))
  }

  it should "send the system prompt as system_instruction" in {
    val bodyJson =
      parse(captureRequestBody(includeTools = true, ConversationHistory.withInitialPrompt("hi"), systemPrompt = Some("Be terse"))).value
    bodyJson.hcursor.downField("system_instruction").as[String] shouldBe Right("Be terse")
  }

  it should "send the response schema as response_format, carrying the case class's fields" in {
    val bodyJson = parse(
      captureRequestBody(
        includeTools = false,
        ConversationHistory.withInitialPrompt("hi"),
        responseSchema = Some(ResponseSchema.derived[GeminiAgentWeatherSummary]())
      )
    ).value

    bodyJson.hcursor.downField("response_format").downField("properties").as[Map[String, Json]].value.keySet should contain("city")
  }

  it should "normalize a tool schema that omits `type` to a minimal object schema" in {
    val tool = new AgentTool[Identity, Map[String, Json]] {
      override def name: String = "no-type-tool"
      override def description: String = "Accepts anything, schema omits type"
      override def jsonSchema: Schema = parse("""{"type":"object"}""").value.as[Schema](sttp.apispec.circe.schemaDecoder).value
      override def codec: io.circe.Codec[Map[String, Json]] = io.circe.Codec.implied
      override def execute(input: Map[String, Json]): Identity[String] = "ok"
      override def rawJsonSchema: Json = Json.obj()
    }

    newBackend(Seq(tool)).convertedTools.head match {
      case Tool.Function(_, _, parameters) =>
        parameters shouldBe Json.obj("type" -> Json.fromString("object"), "properties" -> Json.obj())
      case other => fail(s"expected Tool.Function, got $other")
    }
  }

  it should "map incomplete and budget_exceeded statuses to StopReason.MaxTokens" in {
    val backend = newBackend(Seq.empty)

    val incompleteStub =
      DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust("""{"status":"incomplete"}""", StatusCode.Ok))
    backend
      .sendRequest(ConversationHistory.withInitialPrompt("hi"), incompleteStub, includeTools = false, IterationInfo(1, 10))
      .stopReason shouldBe StopReason.MaxTokens

    val budgetStub =
      DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust("""{"status":"budget_exceeded"}""", StatusCode.Ok))
    backend
      .sendRequest(ConversationHistory.withInitialPrompt("hi"), budgetStub, includeTools = false, IterationInfo(1, 10))
      .stopReason shouldBe StopReason.MaxTokens
  }

  it should "map a queued status with no tool calls to StopReason.Other" in {
    val backend = newBackend(Seq.empty)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust("""{"status":"queued"}""", StatusCode.Ok))

    backend
      .sendRequest(ConversationHistory.withInitialPrompt("hi"), httpStub, includeTools = false, IterationInfo(1, 10))
      .stopReason shouldBe StopReason.Other("queued")
  }

  it should "replay an iteration marker as a user_input step carrying the iteration text" in {
    val history = ConversationHistory.empty.addUserPrompt("go").addIterationMarker(2, 5)
    val bodyJson = parse(captureRequestBody(includeTools = false, history)).value
    val input = bodyJson.hcursor.downField("input")

    input.downN(1).downField("type").as[String] shouldBe Right("user_input")
    input.downN(1).downField("content").downN(0).downField("text").as[String] shouldBe Right("[Iteration 2 of 5]")
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
      ResponseStub.adjust(completedResponse, StatusCode.Ok)
    }

    val client = GeminiClient(GeminiConfig(apiKey = "test-key"))
    val backend = new GeminiAgentBackend[Identity](
      client,
      info => if (info.isLastIteration) GeminiModel.Gemini25Pro else GeminiModel.Gemini25FlashLite,
      Seq.empty,
      None,
      None
    )(IdentityMonad)

    backend.sendRequest(ConversationHistory.withInitialPrompt("hello"), httpStub, includeTools = false, IterationInfo(1, 3)): Unit
    backend.sendRequest(ConversationHistory.withInitialPrompt("hello"), httpStub, includeTools = false, IterationInfo(3, 3)): Unit

    capturedModels.get() shouldBe Vector("gemini-2.5-flash-lite", "gemini-2.5-pro")
  }

  it should "surface provider-reported usage and model on AgentResponse" in {
    val responseJson =
      """{
        |  "id": "int_1",
        |  "status": "completed",
        |  "model": "gemini-2.5-flash",
        |  "steps": [],
        |  "usage": {
        |    "total_input_tokens": 100,
        |    "total_output_tokens": 30,
        |    "total_tokens": 130,
        |    "total_cached_tokens": 40,
        |    "total_thought_tokens": 10
        |  }
        |}""".stripMargin
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust(responseJson, StatusCode.Ok))

    val response = newBackend(Seq.empty).sendRequest(
      ConversationHistory.withInitialPrompt("hello"),
      httpStub,
      includeTools = false,
      IterationInfo(1, 10)
    )

    response.model shouldBe Some("gemini-2.5-flash")
    response.usage shouldBe Some(
      TokenUsage(
        inputTokens = Tokens(100L),
        // total_output_tokens (30) + total_thought_tokens (10): Gemini reports thought tokens separately,
        // and TokenUsage.outputTokens includes reasoning.
        outputTokens = Tokens(40L),
        cachedInputTokens = Tokens(40L),
        reasoningTokens = Tokens(10L)
      )
    )
  }

  it should "build typed and mixed-model agents through GeminiAgent" in {
    GeminiAgent.synchronous(GeminiConfig(apiKey = "test-key"), GeminiModel.Gemini25Flash): Unit
    GeminiAgent.synchronous(
      GeminiConfig(apiKey = "test-key"),
      (info: IterationInfo) => if (info.isLastIteration) GeminiModel.Gemini25Pro else GeminiModel.Gemini25FlashLite
    ): Unit
    GeminiAgent.synchronous(GeminiConfig(apiKey = "test-key"), "gemini-experimental"): Unit
    succeed
  }
}
