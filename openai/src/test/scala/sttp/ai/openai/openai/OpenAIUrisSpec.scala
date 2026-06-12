package sttp.ai.openai

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.model.Uri

class OpenAIUrisSpec extends AnyFlatSpec with Matchers {
  private val azureBase: Uri = uri"https://x.openai.azure.com/openai/deployments/y?api-version=2024-10-21"
  private val plainBase: Uri = uri"https://api.openai.com/v1"

  "OpenAIUris with a base URI carrying a query string" should "keep the query after the path on every endpoint" in {
    val uris = new OpenAIUris(azureBase)

    uris.ChatCompletions.toString shouldBe
      "https://x.openai.azure.com/openai/deployments/y/chat/completions?api-version=2024-10-21": Unit
    uris.Embeddings.toString shouldBe
      "https://x.openai.azure.com/openai/deployments/y/embeddings?api-version=2024-10-21": Unit
    uris.Models.toString shouldBe
      "https://x.openai.azure.com/openai/deployments/y/models?api-version=2024-10-21": Unit
    uris.Transcriptions.toString shouldBe
      "https://x.openai.azure.com/openai/deployments/y/audio/transcriptions?api-version=2024-10-21": Unit
    uris.CreateImage.toString shouldBe
      "https://x.openai.azure.com/openai/deployments/y/images/generations?api-version=2024-10-21": Unit

    uris.ChatCompletions.params.get("api-version") shouldBe Some("2024-10-21")
  }

  "OpenAIUris with a plain base URI" should "build endpoint paths without a query" in {
    val uris = new OpenAIUris(plainBase)

    uris.ChatCompletions.toString shouldBe "https://api.openai.com/v1/chat/completions": Unit
    uris.Embeddings.toString shouldBe "https://api.openai.com/v1/embeddings": Unit
    uris.Models.toString shouldBe "https://api.openai.com/v1/models": Unit
    uris.Transcriptions.toString shouldBe "https://api.openai.com/v1/audio/transcriptions"
  }
}
