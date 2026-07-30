package sttp.ai.openai

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.config.OpenAIConfig
import sttp.client4._
import sttp.model.Uri

class AuthSchemeSpec extends AnyFlatSpec with Matchers {
  private val azureBase: Uri = uri"https://my-res.openai.azure.com/openai/deployments/gpt-4o?api-version=2024-10-21"

  "OpenAI with the default auth scheme" should "send an Authorization: Bearer header" in {
    val request = new OpenAI("test-token").getModels
    request.headers.find(_.name.equalsIgnoreCase("Authorization")).map(_.value) shouldBe Some("Bearer test-token"): Unit
    request.headers.exists(_.name.equalsIgnoreCase("api-key")) shouldBe false
  }

  "OpenAI with AuthScheme.AzureApiKey" should "send an api-key header and no Authorization header" in {
    val request = new OpenAI("azure-key", azureBase, None, AuthScheme.AzureApiKey).getModels
    request.headers.find(_.name.equalsIgnoreCase("api-key")).map(_.value) shouldBe Some("azure-key"): Unit
    request.headers.exists(_.name.equalsIgnoreCase("Authorization")) shouldBe false
  }

  it should "still send the OpenAI-Organization header when organization is set" in {
    val request = new OpenAI("azure-key", azureBase, Some("my-org"), AuthScheme.AzureApiKey).getModels
    request.headers.find(_.name.equalsIgnoreCase("OpenAI-Organization")).map(_.value) shouldBe Some("my-org"): Unit
    request.headers.find(_.name.equalsIgnoreCase("api-key")).map(_.value) shouldBe Some("azure-key")
  }

  "OpenAIConfig.authHeaders" should "contain a bearer Authorization header by default" in {
    val headers = OpenAIConfig(apiKey = "test-token").authHeaders
    headers.get("Authorization") shouldBe Some("Bearer test-token"): Unit
    headers.contains("api-key") shouldBe false
  }

  it should "contain an api-key header for the AzureApiKey scheme" in {
    val headers = OpenAIConfig(apiKey = "azure-key", authScheme = AuthScheme.AzureApiKey).authHeaders
    headers.get("api-key") shouldBe Some("azure-key"): Unit
    headers.contains("Authorization") shouldBe false
  }

  "OpenAI built from a config with AzureApiKey" should "send the api-key header" in {
    val config = OpenAIConfig(apiKey = "azure-key", baseUrl = azureBase, authScheme = AuthScheme.AzureApiKey)
    val request = OpenAI(config).getModels
    request.headers.find(_.name.equalsIgnoreCase("api-key")).map(_.value) shouldBe Some("azure-key"): Unit
    request.headers.exists(_.name.equalsIgnoreCase("Authorization")) shouldBe false
  }
}
