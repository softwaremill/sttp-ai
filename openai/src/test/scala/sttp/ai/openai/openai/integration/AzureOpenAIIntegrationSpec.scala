package sttp.ai.openai.integration

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.{AuthScheme, OpenAISyncClient}
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.{Content, Message}
import sttp.model.Uri

/** Integration tests for Azure OpenAI api-key authentication, run against a real Azure OpenAI deployment.
  *
  * To run these tests, set:
  * {{{
  * export AZURE_OPENAI_API_KEY=your-azure-api-key
  * export AZURE_OPENAI_ENDPOINT="https://my-resource.openai.azure.com/openai/deployments/gpt-4o-mini?api-version=2024-10-21"
  * sbt "testOnly *AzureOpenAIIntegrationSpec"
  * }}}
  *
  * AZURE_OPENAI_ENDPOINT must be the full base URL including the deployment path and api-version query. Optionally set
  * AZURE_OPENAI_DEPLOYMENT to the deployment name sent in the request body (defaults to gpt-4o-mini; classic deployment endpoints ignore
  * it, the newer /openai/v1/ endpoint requires it to match your deployment).
  *
  * If AZURE_OPENAI_API_KEY or AZURE_OPENAI_ENDPOINT is not defined, all tests are skipped (not failed).
  */
class AzureOpenAIIntegrationSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private val maybeApiKey: Option[String] = sys.env.get("AZURE_OPENAI_API_KEY")
  private val maybeEndpoint: Option[String] = sys.env.get("AZURE_OPENAI_ENDPOINT")
  private var clientOpt: Option[OpenAISyncClient] = None

  override def beforeAll(): Unit = {
    super.beforeAll()
    for {
      apiKey <- maybeApiKey
      endpoint <- maybeEndpoint
    } clientOpt = Some(OpenAISyncClient(apiKey, Uri.unsafeParse(endpoint), AuthScheme.AzureApiKey))
  }

  override def afterAll(): Unit = {
    clientOpt.foreach(_.close())
    super.afterAll()
  }

  private def withClient[T](test: OpenAISyncClient => T): T =
    clientOpt match {
      case Some(client) => test(client)
      case None         => cancel("AZURE_OPENAI_API_KEY or AZURE_OPENAI_ENDPOINT not defined - skipping integration test")
    }

  "Azure OpenAI Chat API" should "create a chat completion using api-key authentication" in
    withClient { client =>
      // given
      val chatBody = ChatBody(
        messages = Seq(Message.User(content = Content.TextContent("Say 'hello' and nothing else."))),
        model = ChatCompletionModel.CustomChatCompletionModel(sys.env.getOrElse("AZURE_OPENAI_DEPLOYMENT", "gpt-4o-mini")),
        maxCompletionTokens = Some(50)
      )

      // when
      val response = client.createChatCompletion(chatBody)

      // then
      response.choices should not be empty
      ()
    }
}
