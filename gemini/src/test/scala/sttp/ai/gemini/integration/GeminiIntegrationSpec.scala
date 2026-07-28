package sttp.ai.gemini.integration

import io.circe.generic.semiauto.deriveCodec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.models.InteractionStatus
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

  private val testModel = "gemini-3.5-flash-lite"

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
        .simple(testModel, "Reply with exactly one word: ping")
        .copy(store = Some(false))
    )

    response.status shouldBe InteractionStatus.Completed
    response.outputText.toLowerCase should include("ping")
  }

  it should "return structured output via createInteractionAs" in withClient { client =>
    val answer = client.createInteractionAs[CityAnswer](
      InteractionRequest
        .simple(testModel, "What is the capital of France?")
        .copy(store = Some(false))
    )

    answer.city.toLowerCase should include("paris")
  }

  it should "store, retrieve and delete an interaction" in withClient { client =>
    val created = client.createInteraction(
      InteractionRequest.simple(testModel, "Reply with exactly one word: stored").copy(store = Some(true))
    )

    created.id shouldBe defined

    val fetched = client.getInteraction(created.id.get)
    fetched.id shouldBe created.id

    client.deleteInteraction(created.id.get)
  }
}
