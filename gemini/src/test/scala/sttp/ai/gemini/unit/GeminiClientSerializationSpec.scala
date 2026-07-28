package sttp.ai.gemini.unit

import io.circe.parser.parse
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.GeminiClientImpl
import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.models._
import sttp.ai.gemini.requests.InteractionRequest
import sttp.client4.{DefaultSyncBackend, StringBody}
import sttp.client4.testing.ResponseStub
import sttp.model.{Header, ResponseMetadata, StatusCode}

/** Phantom tag for a minimal test [[sttp.capabilities.Streams]] instance. Tagging with `TestCapability` rather than `TestStreams.type`
  * avoids "illegal cyclic reference involving object TestStreams" under Scala 2.13 (Scala 3 tolerates the self-referential singleton type,
  * but 2.13 does not).
  */
trait TestCapability
object TestStreams extends sttp.capabilities.Streams[TestCapability] {
  override type BinaryStream = Unit
  override type Pipe[A, B] = Unit
}

class GeminiClientSerializationSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val client = new GeminiClientImpl(GeminiConfig("test-key"))

  private def bodyOf(request: InteractionRequest): String =
    client.createInteraction(request).body match {
      case StringBody(s, _, _) => s
      case other               => fail(s"expected StringBody, got $other")
    }

  "GeminiClientImpl" should "target POST v1beta/interactions with the x-goog-api-key header" in {
    val req = client.createInteraction(InteractionRequest.simple("gemini-2.5-flash-lite", "hi"))
    req.uri.toString should endWith("/v1beta/interactions")
    req.method.method shouldBe "POST"
    req.headers should contain(Header("x-goog-api-key", "test-key"))
    req.headers should contain(Header("content-type", "application/json"))
  }

  it should "drop unset optional fields from the serialized body" in {
    val bodyJson = parse(bodyOf(InteractionRequest.simple("gemini-2.5-flash-lite", "hi"))).value
    bodyJson.hcursor.downField("system_instruction").succeeded shouldBe false
  }

  it should "preserve tool parameters verbatim, including legitimate nulls" in {
    val schema = parse("""{"type":"object","properties":{"level":{"enum":["low","high",null],"default":null}}}""").value
    val request = InteractionRequest
      .simple("gemini-2.5-flash-lite", "hi")
      .copy(tools = Some(List(Tool.Function("set-level", Some("Sets level"), schema))))

    val bodyJson = parse(bodyOf(request)).value
    bodyJson.hcursor.downField("tools").downN(0).downField("parameters").focus shouldBe Some(schema)
  }

  it should "preserve response_format schemas verbatim, including legitimate nulls" in {
    val schema = parse("""{"type":"object","properties":{"level":{"enum":["low","high",null],"default":null}}}""").value
    val request =
      InteractionRequest.simple("gemini-3.5-flash-lite", "hi").copy(responseFormat = Some(ResponseFormat.JsonSchema(schema)))

    parse(bodyOf(request)).value.hcursor.downField("response_format").focus shouldBe Some(schema)
  }

  it should "preserve replayed function_call arguments and function_result results verbatim" in {
    val arguments = parse("""{"city":"Paris","unit":null}""").value
    val result = parse("""{"temp":20,"error":null}""").value
    val request = InteractionRequest(
      model = "gemini-3.5-flash-lite",
      input = InteractionInput.StepsInput(
        List(Step.FunctionCall("c1", "get_weather", arguments), Step.FunctionResult("c1", "get_weather", result))
      )
    )

    val input = parse(bodyOf(request)).value.hcursor.downField("input")
    input.downN(0).downField("arguments").focus shouldBe Some(arguments)
    input.downN(1).downField("result").focus shouldBe Some(result)
  }

  it should "map error responses by HTTP status code" in {
    val errorBody = """{"error":{"code":429,"message":"quota exceeded","status":"RESOURCE_EXHAUSTED"}}"""
    def meta(status: StatusCode) = ResponseMetadata(status, "", Nil)

    client.mapErrorToException(errorBody, meta(StatusCode.TooManyRequests)) shouldBe a[GeminiException.RateLimitException]
    client.mapErrorToException(errorBody, meta(StatusCode.Unauthorized)) shouldBe a[GeminiException.AuthenticationException]
    client.mapErrorToException(errorBody, meta(StatusCode.Forbidden)) shouldBe a[GeminiException.PermissionException]
    client.mapErrorToException(errorBody, meta(StatusCode.BadRequest)) shouldBe a[GeminiException.InvalidRequestException]
    client.mapErrorToException(errorBody, meta(StatusCode.NotFound)) shouldBe a[GeminiException.NotFoundException]
    client.mapErrorToException(errorBody, meta(StatusCode.ServiceUnavailable)) shouldBe a[GeminiException.ServiceUnavailableException]
    client.mapErrorToException(errorBody, meta(StatusCode.InternalServerError)) shouldBe a[GeminiException.APIException]
    client.mapErrorToException(errorBody, meta(StatusCode.TooManyRequests)).getMessage shouldBe "quota exceeded"
    client.mapErrorToException(errorBody, meta(StatusCode.TooManyRequests)).code shouldBe Some("429")
  }

  it should "map error responses whose code is a JSON string, not just an int" in {
    val errorBody = """{"error":{"message":"quota exceeded","code":"rate_limit"}}"""
    def meta(status: StatusCode) = ResponseMetadata(status, "", Nil)

    val ex = client.mapErrorToException(errorBody, meta(StatusCode.TooManyRequests))
    ex.getMessage shouldBe "quota exceeded"
    ex.code shouldBe Some("rate_limit")
  }

  it should "map non-2xx responses through the status-based exception dispatch" in {
    val errorBody = """{"error":{"code":401,"message":"invalid key","status":"UNAUTHENTICATED"}}"""
    val stub = DefaultSyncBackend.stub.whenAnyRequest
      .thenRespondF(_ => ResponseStub.adjust(errorBody, StatusCode.Unauthorized))
    val result = client.createInteraction(InteractionRequest.simple("gemini-2.5-flash-lite", "hi")).send(stub).body
    result.left.toOption.get shouldBe a[GeminiException.AuthenticationException]
    result.left.toOption.get.getMessage shouldBe "invalid key"
  }

  it should "map a non-JSON error body to an exception carrying the raw body as message" in {
    val errorBody = "<html>Service Unavailable</html>"
    val stub = DefaultSyncBackend.stub.whenAnyRequest
      .thenRespondF(_ => ResponseStub.adjust(errorBody, StatusCode.ServiceUnavailable))
    val result = client.createInteraction(InteractionRequest.simple("gemini-2.5-flash-lite", "hi")).send(stub).body
    result.left.toOption.get shouldBe a[GeminiException.ServiceUnavailableException]
    result.left.toOption.get.getMessage shouldBe errorBody
  }

  it should "map an error body without a message to an exception carrying the raw body as message" in {
    val errorBody = """{"error":{"code":400}}"""
    val stub = DefaultSyncBackend.stub.whenAnyRequest
      .thenRespondF(_ => ResponseStub.adjust(errorBody, StatusCode.BadRequest))
    val result = client.createInteraction(InteractionRequest.simple("gemini-2.5-flash-lite", "hi")).send(stub).body
    result.left.toOption.get shouldBe a[GeminiException.InvalidRequestException]
    result.left.toOption.get.getMessage shouldBe errorBody
  }

  it should "map a malformed 200 body to a DeserializationGeminiException" in {
    val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust("not json", StatusCode.Ok))
    val result = client.createInteraction(InteractionRequest.simple("gemini-2.5-flash-lite", "hi")).send(stub).body
    result.left.toOption.get shouldBe a[GeminiException.DeserializationGeminiException]
  }

  it should "map deleteInteraction responses via asUnit_parseErrors" in {
    val okStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust("", StatusCode.Ok))
    client.deleteInteraction("int_1").send(okStub).body shouldBe Right(())

    val errorBody = """{"error":{"code":404,"message":"not found","status":"NOT_FOUND"}}"""
    val notFoundStub = DefaultSyncBackend.stub.whenAnyRequest
      .thenRespondF(_ => ResponseStub.adjust(errorBody, StatusCode.NotFound))
    client.deleteInteraction("int_1").send(notFoundStub).body.left.toOption.get shouldBe a[GeminiException.NotFoundException]
  }

  it should "target GET v1beta/interactions/{id} for getInteraction" in {
    val req = client.getInteraction("int_1")
    req.uri.toString should endWith("/v1beta/interactions/int_1")
    req.method.method shouldBe "GET"
  }

  it should "target DELETE v1beta/interactions/{id} for deleteInteraction" in {
    val req = client.deleteInteraction("int_1")
    req.uri.toString should endWith("/v1beta/interactions/int_1")
    req.method.method shouldBe "DELETE"
  }

  it should "target POST v1beta/interactions/{id}/cancel for cancelInteraction" in {
    val req = client.cancelInteraction("int_1")
    req.uri.toString should endWith("/v1beta/interactions/int_1/cancel")
    req.method.method shouldBe "POST"
  }

  it should "set stream=true on streaming request bodies" in {
    val req = client.createInteractionAsInputStream(InteractionRequest.simple("gemini-2.5-flash-lite", "hi"))
    req.body match {
      case StringBody(s, _, _) => s should include("\"stream\":true")
      case other               => fail(s"expected StringBody, got $other")
    }
  }

  it should "set stream=true on binary-stream request bodies" in {
    val req = client.createInteractionAsBinaryStream(TestStreams, InteractionRequest.simple("gemini-3.5-flash-lite", "hi"))
    req.body match {
      case StringBody(s, _, _) => parse(s).value.hcursor.downField("stream").as[Boolean] shouldBe Right(true)
      case other               => fail(s"expected StringBody, got $other")
    }
  }
}
