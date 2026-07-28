package sttp.ai.gemini.unit

import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.parse
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.models.{GeminiModel, ResponseFormat}
import sttp.ai.gemini.requests.InteractionRequest
import sttp.client4.testing.ResponseStub
import sttp.client4.{DefaultSyncBackend, GenericRequest, StringBody}
import sttp.model.StatusCode
import sttp.tapir.{Schema => TapirSchema}

import java.util.concurrent.atomic.AtomicReference

case class WeatherReport(city: String, temperature: Double)

object WeatherReport {
  implicit val codec: io.circe.Codec[WeatherReport] = deriveCodec
  implicit val schema: TapirSchema[WeatherReport] = TapirSchema.derived
}

class GeminiSyncClientSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val testModel = GeminiModel.Gemini35FlashLite.value

  private val completedResponse =
    """{"id":"int_1","status":"completed",
      |"steps":[{"type":"model_output","content":[{"type":"text","text":"{\"city\":\"Warsaw\",\"temperature\":21.5}"}]}]}""".stripMargin

  private def clientReturning(body: String, status: StatusCode = StatusCode.Ok): GeminiSyncClient = {
    val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF(_ => ResponseStub.adjust(body, status))
    GeminiSyncClient(GeminiConfig("test-key"), stub)
  }

  "GeminiSyncClient" should "return the interaction response on success" in {
    val response = clientReturning(completedResponse).createInteraction(InteractionRequest.simple(testModel, "hi"))
    response.id shouldBe Some("int_1")
  }

  it should "throw the mapped exception on API errors" in {
    val error = """{"error":{"code":401,"message":"invalid key","status":"UNAUTHENTICATED"}}"""
    an[GeminiException.AuthenticationException] should be thrownBy
      clientReturning(error, StatusCode.Unauthorized).createInteraction(InteractionRequest.simple(testModel, "hi"))
  }

  it should "decode structured output via createInteractionAs" in {
    val result = clientReturning(completedResponse)
      .createInteractionAs[WeatherReport](InteractionRequest.simple(testModel, "weather in Warsaw"))
    result shouldBe WeatherReport("Warsaw", 21.5)
  }

  it should "throw a deserialization exception when structured output does not parse" in {
    val badResponse =
      """{"id":"int_1","status":"completed","steps":[{"type":"model_output","content":[{"type":"text","text":"not json"}]}]}"""
    an[GeminiException.DeserializationGeminiException] should be thrownBy
      clientReturning(badResponse).createInteractionAs[WeatherReport](InteractionRequest.simple(testModel, "hi"))
  }

  private def captureCreateInteractionAsBody(request: InteractionRequest): String = {
    val captured = new AtomicReference[GenericRequest[_, _]](null)
    val httpStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { req =>
      captured.set(req)
      ResponseStub.adjust(completedResponse, StatusCode.Ok)
    }
    GeminiSyncClient(GeminiConfig("test-key"), httpStub).createInteractionAs[WeatherReport](request): Unit
    captured.get().body match {
      case StringBody(s, _, _) => s
      case other               => fail(s"expected StringBody, got $other")
    }
  }

  it should "derive a response_format schema from T's tapir schema when the request has none" in {
    val bodyJson = parse(captureCreateInteractionAsBody(InteractionRequest.simple(testModel, "weather"))).value
    bodyJson.hcursor.downField("response_format").downField("properties").downField("city").succeeded shouldBe true
  }

  it should "leave an existing response_format untouched" in {
    val customSchema = parse("""{"type":"object","properties":{"custom":{"type":"string"}}}""").value
    val request = InteractionRequest
      .simple(testModel, "weather")
      .copy(responseFormat = Some(ResponseFormat.JsonSchema(customSchema)))

    val bodyJson = parse(captureCreateInteractionAsBody(request)).value
    bodyJson.hcursor.downField("response_format").focus shouldBe Some(customSchema)
  }
}
