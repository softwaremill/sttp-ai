package sttp.ai.gemini.integration

import io.circe.generic.semiauto.deriveCodec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.models.{GeminiModel, GenerationConfig, InteractionStatus}
import sttp.ai.gemini.requests.InteractionRequest
import sttp.tapir.{Schema => TSchema}

case class CityAnswer(city: String)

object CityAnswer {
  implicit val codec: io.circe.Codec[CityAnswer] = deriveCodec
  implicit val schema: TSchema[CityAnswer] = TSchema.derived[CityAnswer]
}

/** Integration tests against the real Gemini Interactions API. Cost-efficient: cheapest model, minimal tokens.
  *
  * To run: `export GEMINI_API_KEY=...` then `sbt "testOnly *GeminiIntegrationSpec"`. Skipped (not failed) when GEMINI_API_KEY is unset.
  */
class GeminiIntegrationSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private val testModel = GeminiModel.Gemini35FlashLite.value

  // gemini-3.5-flash-lite bills "thought" tokens against max_output_tokens, so a too-small cap would truncate replies to
  // status=incomplete before any visible output is produced. 1024 leaves enough headroom for both thinking and the short answer.
  private val testGenerationConfig = Some(GenerationConfig(maxOutputTokens = Some(1024)))

  private var clientOpt: Option[GeminiSyncClient] = None
  private val maybeApiKey: Option[String] = sys.env.get("GEMINI_API_KEY")

  override def beforeAll(): Unit = {
    super.beforeAll()
    maybeApiKey.foreach(apiKey => clientOpt = Some(GeminiSyncClient(GeminiConfig(apiKey = apiKey))))
  }

  override def afterAll(): Unit = {
    clientOpt.foreach(_.close())
    super.afterAll()
  }

  private def withClient(test: GeminiSyncClient => Unit): Unit =
    clientOpt match {
      case Some(client) => test(client)
      case None         => cancel("GEMINI_API_KEY not set - skipping integration test")
    }

  "GeminiSyncClient" should "create a simple interaction" in withClient { client =>
    val response = client.createInteraction(
      InteractionRequest
        .simple(testModel, "Reply with a short greeting.")
        .copy(store = Some(false), generationConfig = testGenerationConfig)
    )

    response.status shouldBe InteractionStatus.Completed
    response.outputText.trim should not be empty
  }

  it should "return structured output via createInteractionAs" in withClient { client =>
    val answer = client.createInteractionAs[CityAnswer](
      InteractionRequest
        .simple(testModel, "What is the capital of France?")
        .copy(store = Some(false), generationConfig = testGenerationConfig)
    )

    answer.city.toLowerCase should include("paris")
  }

  it should "store, retrieve and delete an interaction" in withClient { client =>
    val created = client.createInteraction(
      InteractionRequest
        .simple(testModel, "Reply with exactly one word: stored")
        .copy(store = Some(true), generationConfig = testGenerationConfig)
    )

    created.id shouldBe defined

    val fetched = client.getInteraction(created.id.get)
    fetched.id shouldBe created.id

    client.deleteInteraction(created.id.get)
  }

  // No GEMINI_API_KEY required: this hits the real API with a deliberately invalid key, so it runs unconditionally
  // (unlike the tests above, it must NOT go through withClient/cancel).
  it should "map a real API error for an invalid key" in {
    val client = GeminiSyncClient(GeminiConfig(apiKey = "invalid-test-key"))
    try
      the[GeminiException.InvalidRequestException] thrownBy
        client.createInteraction(InteractionRequest.simple(testModel, "hi").copy(store = Some(false))) should have message
        "API key not valid. Please pass a valid API key."
    finally client.close()
  }
}
