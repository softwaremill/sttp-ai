package sttp.ai.gemini.unit.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.config.{GeminiAuth, GeminiConfig, GeminiEndpoint}
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

  it should "call the Developer API with the API key by default" in {
    val config = GeminiConfig("test-api-key")

    config.endpoint shouldBe GeminiEndpoint.DeveloperApi
    config.auth shouldBe GeminiAuth.ApiKey
  }

  "GeminiConfig.vertex" should "target the Vertex AI interactions resource of the project" in {
    val config = GeminiConfig.vertex(project = "my-project", accessToken = () => "token")

    config.baseUrl shouldBe Uri.unsafeParse("https://aiplatform.googleapis.com")
    config.endpoint shouldBe GeminiEndpoint.Vertex("my-project", "global")
    config.endpoint.interactionsPath shouldBe List("v1beta1", "projects", "my-project", "locations", "global", "interactions")
  }

  it should "send the access token as a bearer token, and no API key" in {
    val config = GeminiConfig.vertex(project = "my-project", accessToken = () => "token")

    config.authHeaders shouldBe Map(
      "Authorization" -> "Bearer token",
      "content-type" -> "application/json"
    )
  }

  it should "ask for the access token on every request, so a refreshed token is used" in {
    val tokens = Iterator("first", "second")
    val config = GeminiConfig.vertex(project = "my-project", accessToken = () => tokens.next())

    config.authHeaders("Authorization") shouldBe "Bearer first"
    config.authHeaders("Authorization") shouldBe "Bearer second"
  }

  it should "use the host of a regional location" in {
    val config = GeminiConfig.vertex(project = "my-project", accessToken = () => "token", location = "us-central1")

    config.baseUrl shouldBe Uri.unsafeParse("https://us-central1-aiplatform.googleapis.com")
    config.endpoint shouldBe GeminiEndpoint.Vertex("my-project", "us-central1")
  }

  it should "use a given host instead of the location's" in {
    val host = Uri.unsafeParse("http://localhost:8080")
    val config = GeminiConfig.vertex(project = "my-project", accessToken = () => "token", baseUrl = Some(host))

    config.baseUrl shouldBe host
  }

  "GeminiEndpoint.Vertex" should "refuse an empty project or location" in {
    an[IllegalArgumentException] should be thrownBy GeminiEndpoint.Vertex("", "global")
    an[IllegalArgumentException] should be thrownBy GeminiEndpoint.Vertex("my-project", "")
  }
}
