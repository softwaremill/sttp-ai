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
    val body = bodyOf(InteractionRequest.simple("gemini-2.5-flash-lite", "hi"))
    body should not include "\"system_instruction\""
    body should not include "null"
  }

  it should "preserve tool parameters verbatim, including legitimate nulls" in {
    val schema = parse("""{"type":"object","properties":{"level":{"enum":["low","high",null],"default":null}}}""").value
    val request = InteractionRequest
      .simple("gemini-2.5-flash-lite", "hi")
      .copy(tools = Some(List(Tool.Function("set-level", Some("Sets level"), schema))))

    val bodyJson = parse(bodyOf(request)).value
    bodyJson.hcursor.downField("tools").downN(0).downField("parameters").focus shouldBe Some(schema)
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
  }

  it should "map error responses whose code is a JSON string, not just an int" in {
    val errorBody = """{"error":{"message":"quota exceeded","code":"rate_limit"}}"""
    def meta(status: StatusCode) = ResponseMetadata(status, "", Nil)

    val ex = client.mapErrorToException(errorBody, meta(StatusCode.TooManyRequests))
    ex shouldBe a[GeminiException.RateLimitException]
    ex.getMessage shouldBe "quota exceeded"
  }

  it should "map non-2xx responses through the status-based exception dispatch" in {
    val errorBody = """{"error":{"code":401,"message":"invalid key","status":"UNAUTHENTICATED"}}"""
    val stub = DefaultSyncBackend.stub.whenAnyRequest
      .thenRespondF(_ => ResponseStub.adjust(errorBody, StatusCode.Unauthorized))
    val result = client.createInteraction(InteractionRequest.simple("gemini-2.5-flash-lite", "hi")).send(stub).body
    result.left.toOption.get shouldBe a[GeminiException.AuthenticationException]
    result.left.toOption.get.getMessage shouldBe "invalid key"
  }

  it should "set stream=true on streaming request bodies" in {
    val req = client.createInteractionAsInputStream(InteractionRequest.simple("gemini-2.5-flash-lite", "hi"))
    req.body match {
      case StringBody(s, _, _) => s should include("\"stream\":true")
      case other               => fail(s"expected StringBody, got $other")
    }
  }
}
