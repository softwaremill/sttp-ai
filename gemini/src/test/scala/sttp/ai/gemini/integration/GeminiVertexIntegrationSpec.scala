package sttp.ai.gemini.integration

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.models.{GeminiModel, GenerationConfig, InteractionStatus}
import sttp.ai.gemini.requests.InteractionRequest

/** Integration tests against the Interactions API on Vertex AI.
  *
  * To run: `export GEMINI_VERTEX_PROJECT=my-project GEMINI_VERTEX_ACCESS_TOKEN=$(gcloud auth print-access-token)`, then
  * `sbt "testOnly *GeminiVertexIntegrationSpec"`. Optionally `GEMINI_VERTEX_MODEL` (defaults to `gemini-3.6-flash`). Skipped (not failed)
  * when the project or the token is unset.
  */
class GeminiVertexIntegrationSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private val testModel = sys.env.getOrElse("GEMINI_VERTEX_MODEL", GeminiModel.Gemini36Flash.value)
  private val testGenerationConfig = Some(GenerationConfig(maxOutputTokens = Some(1024)))

  private var clientOpt: Option[GeminiSyncClient] = None

  override def beforeAll(): Unit = {
    super.beforeAll()
    for {
      project <- sys.env.get("GEMINI_VERTEX_PROJECT")
      token <- sys.env.get("GEMINI_VERTEX_ACCESS_TOKEN")
    } clientOpt = Some(GeminiSyncClient(GeminiConfig.vertex(project = project, accessToken = () => token)))
  }

  override def afterAll(): Unit = {
    clientOpt.foreach(_.close())
    super.afterAll()
  }

  private def withClient(test: GeminiSyncClient => Unit): Unit =
    clientOpt match {
      case Some(client) => test(client)
      case None         => cancel("GEMINI_VERTEX_PROJECT or GEMINI_VERTEX_ACCESS_TOKEN not set - skipping integration test")
    }

  "GeminiSyncClient on Vertex AI" should "create a simple interaction" in withClient { client =>
    val response = client.createInteraction(
      InteractionRequest
        .simple(testModel, "Reply with a short greeting.")
        .copy(store = Some(false), generationConfig = testGenerationConfig)
    )

    response.status shouldBe InteractionStatus.Completed
    response.outputText.trim should not be empty
  }
}
