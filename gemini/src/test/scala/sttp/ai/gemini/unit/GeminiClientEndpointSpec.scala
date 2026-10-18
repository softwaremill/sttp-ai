package sttp.ai.gemini.unit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.GeminiClientImpl
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.requests.InteractionRequest
import sttp.capabilities.Streams
import sttp.client4.GenericRequest
import sttp.model.Uri

class GeminiClientEndpointSpec extends AnyFlatSpec with Matchers {

  private trait TestStreams extends Streams[TestStreams] {
    override type BinaryStream = Nothing
    override type Pipe[A, B] = Nothing
  }
  private object TestStreams extends TestStreams

  private val request = InteractionRequest.simple("gemini-3.6-flash", "hi")

  private val vertex = new GeminiClientImpl(GeminiConfig.vertex(project = "my-project", accessToken = () => "token"))

  private val vertexInteractions = "https://aiplatform.googleapis.com/v1beta1/projects/my-project/locations/global/interactions"

  private def headers(request: GenericRequest[_, _]): Map[String, String] =
    request.headers.map(h => h.name.toLowerCase -> h.value).toMap

  "GeminiClientImpl" should "call the Vertex AI interactions resource of the project for every method" in {
    vertex.createInteraction(request).uri shouldBe Uri.unsafeParse(vertexInteractions)
    vertex.createInteractionAsInputStream(request).uri shouldBe Uri.unsafeParse(vertexInteractions)
    vertex.createInteractionAsBinaryStream(TestStreams, request).uri shouldBe Uri.unsafeParse(vertexInteractions)
    vertex.getInteraction("int_1").uri shouldBe Uri.unsafeParse(s"$vertexInteractions/int_1")
    vertex.deleteInteraction("int_1").uri shouldBe Uri.unsafeParse(s"$vertexInteractions/int_1")
    vertex.cancelInteraction("int_1").uri shouldBe Uri.unsafeParse(s"$vertexInteractions/int_1/cancel")
  }

  it should "authenticate Vertex AI requests with the bearer token and no API key" in {
    val sent = headers(vertex.createInteraction(request))

    sent.get("authorization") shouldBe Some("Bearer token")
    sent.keySet should not contain "x-goog-api-key"
  }

  it should "ask for a new access token for each request it builds" in {
    val tokens = Iterator("first", "second")
    val client = new GeminiClientImpl(GeminiConfig.vertex(project = "my-project", accessToken = () => tokens.next()))

    headers(client.createInteraction(request)).get("authorization") shouldBe Some("Bearer first")
    headers(client.getInteraction("int_1")).get("authorization") shouldBe Some("Bearer second")
  }
}
