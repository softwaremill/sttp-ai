package sttp.ai.gemini.unit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.GeminiClientImpl
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.requests.InteractionRequest

import scala.concurrent.duration.{Duration, DurationInt}

class GeminiClientTimeoutSpec extends AnyFlatSpec with Matchers {

  private val request = InteractionRequest.simple("gemini-2.5-flash-lite", "hi")

  private def client(timeout: Duration) = new GeminiClientImpl(GeminiConfig(apiKey = "test-key", timeout = timeout))

  "GeminiClientImpl" should "apply the configured timeout to createInteraction requests" in {
    client(5.seconds).createInteraction(request).options.readTimeout shouldBe 5.seconds
  }

  it should "apply the configured timeout to streaming (input stream) requests" in {
    client(5.seconds).createInteractionAsInputStream(request).options.readTimeout shouldBe 5.seconds
  }

  it should "apply the configured timeout to getInteraction requests" in {
    client(5.seconds).getInteraction("int_1").options.readTimeout shouldBe 5.seconds
  }

  it should "apply the default 10-minute timeout when none is configured" in {
    new GeminiClientImpl(GeminiConfig(apiKey = "test-key")).createInteraction(request).options.readTimeout shouldBe
      GeminiConfig.DefaultTimeout
  }

  it should "carry a non-finite timeout through (disabling the backend timeout)" in {
    client(Duration.Inf).createInteraction(request).options.readTimeout shouldBe Duration.Inf
  }
}
