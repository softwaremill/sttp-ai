package sttp.ai.gemini.unit.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.config.GeminiConfig
import sttp.model.Uri

class GeminiConfigSpec extends AnyFlatSpec with Matchers {

  "GeminiConfig" should "create config with minimal parameters" in {
    val config = GeminiConfig("test-api-key")

    config.apiKey shouldBe "test-api-key"
    config.baseUrl shouldBe Uri.unsafeParse("https://generativelanguage.googleapis.com")
    config.maxRetries shouldBe 3
    config.organization shouldBe None
  }

  it should "provide Gemini auth headers" in {
    val config = GeminiConfig("test-api-key")

    config.authHeaders shouldBe Map(
      "x-goog-api-key" -> "test-api-key",
      "content-type" -> "application/json"
    )
  }

  it should "accept a custom base URL" in {
    val config = GeminiConfig("test-api-key", baseUrl = Uri.unsafeParse("http://localhost:8080"))

    config.baseUrl shouldBe Uri.unsafeParse("http://localhost:8080")
  }
}
