package sttp.openai.integration

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import sttp.openai.OpenAIExceptions.OpenAIException
import sttp.openai.OpenAISyncClient
import sttp.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.openai.requests.completions.chat.message.{Content, Message}
import sttp.openai.requests.embeddings.EmbeddingsRequestBody.{EmbeddingsBody, EmbeddingsInput, EmbeddingsModel}
import sttp.openai.requests.moderations.ModerationsRequestBody.ModerationsBody
import sttp.openai.requests.responses.ResponsesModel.GPT4oMini
import sttp.openai.requests.responses.{GetResponseQueryParameters, ListInputItemsQueryParameters, ResponsesRequestBody}

// Suppress warnings for unused assertions - these are test assertions that verify behavior
//noinspection ScalaUnusedSymbol

import scala.util.{Failure, Try}

/** Integration tests for sttp-openai library that test against the real OpenAI API.
  *
  * These tests are designed to be cost-efficient by using free/low-cost endpoints and minimal token usage.
  *
  * To run these tests, set the OPENAI_API_KEY environment variable:
  * {{{
  * export OPENAI_API_KEY=your-api-key-here
  * sbt "testOnly *OpenAIIntegrationSpec"
  * }}}
  *
  * If OPENAI_API_KEY is not defined, all tests will be skipped (not failed).
  */
class OpenAIIntegrationSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll with Eventually {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(30, Seconds), interval = Span(500, Millis))

  private var clientOpt: Option[OpenAISyncClient] = None
  private val maybeApiKey: Option[String] = sys.env.get("OPENAI_API_KEY")

  override def beforeAll(): Unit = {
    super.beforeAll()
    maybeApiKey.foreach { apiKey =>
      clientOpt = Some(OpenAISyncClient(apiKey))
    }
  }

  override def afterAll(): Unit = {
    clientOpt.foreach(_.close())
    super.afterAll()
  }

  private def withClient[T](test: OpenAISyncClient => T): T = {
    if (maybeApiKey.isEmpty) {
      cancel("OPENAI_API_KEY not defined - skipping integration test")
    }
    clientOpt match {
      case Some(client) => test(client)
      case None         => fail("OpenAI client not initialized")
    }
  }

  "OpenAI Models API" should "list available models successfully" in
    withClient { client =>
      // given
      // No setup needed - getModels is a free endpoint

      // when
      val models = client.getModels

      // then
      models should not be null
      models.`object` shouldBe "list"
      models.data should not be empty
      models.data.foreach { model =>
        model.id should not be empty
        model.`object` shouldBe "model"
        model.ownedBy should not be empty
      }
      ()
    }

  it should "retrieve a specific model successfully" in
    withClient { client =>
      // given
      val modelId = "gpt-4o-mini" // A commonly available model

      // when
      val model = client.retrieveModel(modelId)

      // then
      model should not be null
      model.id shouldBe modelId
      model.`object` shouldBe "model"
      model.ownedBy should not be empty
      ()
    }

  "OpenAI Moderations API" should "moderate content successfully" in
    withClient { client =>
      // given
      val harmlessText = "Hello, how are you today? This is a test message."
      val moderationBody = ModerationsBody(input = harmlessText)

      // when
      val moderation = client.createModeration(moderationBody)

      // then
      moderation should not be null
      moderation.id should not be empty
      moderation.model.value should not be empty
      moderation.results should not be empty
      moderation.results.head.flagged shouldBe false // Harmless text should not be flagged
      moderation.results.head.categories should not be null
      ()
    }

  it should "flag inappropriate content" in
    withClient { client =>
      // given
      val inappropriateText = "I want to hurt someone badly"
      val moderationBody = ModerationsBody(input = inappropriateText)

      // when
      val moderation = client.createModeration(moderationBody)

      // then
      moderation should not be null
      moderation.results should not be empty
      // Note: We don't assert flagged=true as moderation results may vary
      // but we verify the structure is correct
      moderation.results.head.categories should not be null
      moderation.results.head.categoryScores should not be null
      ()
    }

  "OpenAI Embeddings API" should "create embeddings for text successfully" in
    withClient { client =>
      // given
      val shortText = "test" // Minimal text to reduce cost
      val embeddingsBody = EmbeddingsBody(
        model = EmbeddingsModel.CustomEmbeddingsModel("text-embedding-3-small"), // Cheaper embedding model
        input = EmbeddingsInput.SingleInput(shortText)
      )

      // when
      val embeddings = client.createEmbeddings(embeddingsBody)

      // then
      embeddings should not be null
      embeddings.`object` shouldBe "list"
      embeddings.model.value should not be empty
      embeddings.data should not be empty
      embeddings.data.head.`object` shouldBe "embedding"
      embeddings.data.head.embedding should not be empty
      embeddings.data.head.index shouldBe 0
      embeddings.usage should not be null
      embeddings.usage.totalTokens should be > 0
      ()
    }

  "OpenAI Chat Completions API" should "create a chat completion successfully" in
    withClient { client =>
      // given
      val messages = Seq(
        Message.UserMessage(
          content = Content.TextContent("Hi") // Minimal message to reduce cost
        )
      )
      val chatBody = ChatBody(
        model = ChatCompletionModel.GPT4oMini, // Cheapest GPT model
        messages = messages,
        maxTokens = Some(5) // Limit tokens to minimize cost
      )

      // when
      val response = client.createChatCompletion(chatBody)

      // then
      response should not be null
      response.id should not be empty
      response.`object` shouldBe "chat.completion"
      response.model should not be empty
      response.choices should not be empty
      response.choices.head.message.role.value shouldBe "assistant"
      response.choices.head.message.content should not be empty
      response.usage should not be null
      response.usage.totalTokens should be > 0
      response.usage.totalTokens should be <= 20 // Should be very low due to our constraints
      ()
    }

  "OpenAI Responses API" should "create, retrieve, list input items, and delete a model response successfully" in
    withClient { client =>
      // given
      val requestBody = ResponsesRequestBody(
        model = Some(GPT4oMini), // Using the cheapest available model
        input = Some(Left("Hi")) // Simple text input to minimize cost
      )

      // when - Step 1: Create response
      val createdResponse = client.createModelResponse(requestBody)

      // then - Validate creation
      createdResponse should not be null
      createdResponse.id should not be empty
      createdResponse.`object` shouldBe "response"
      createdResponse.status shouldBe "completed"
      createdResponse.output should not be empty
      createdResponse.output.head should be(a[sttp.openai.requests.responses.ResponsesResponseBody.OutputItem.OutputMessage])
      createdResponse.createdAt should be > 0L

      // Verify that usage is reported (helpful for cost tracking)
      createdResponse.usage should not be null
      createdResponse.usage.foreach { usage =>
        usage.totalTokens should be > 0
      }

      // when - Step 2: Retrieve response by ID
      val retrievedResponse = client.getModelResponse(
        createdResponse.id,
        GetResponseQueryParameters()
      )

      // then - Validate retrieval
      retrievedResponse should not be null
      retrievedResponse.id shouldBe createdResponse.id
      retrievedResponse.`object` shouldBe "response"
      retrievedResponse.status shouldBe "completed"
      retrievedResponse.output should not be empty
      retrievedResponse.model shouldBe createdResponse.model
      retrievedResponse.createdAt shouldBe createdResponse.createdAt

      // when - Step 3: List input items for the response
      val inputItems = client.listResponsesInputItems(
        createdResponse.id,
        ListInputItemsQueryParameters.empty
      )

      // then - Validate input items listing
      inputItems should not be null
      inputItems.`object` shouldBe "list"
      inputItems.data should not be empty

      // Should contain the original user input message with our text "Hi"
      val userInputMessage =
        inputItems.data.find(_.isInstanceOf[sttp.openai.requests.responses.InputItemsListResponseBody.InputItem.InputMessage])
      userInputMessage should be(defined)

      val inputMessage = userInputMessage.get.asInstanceOf[sttp.openai.requests.responses.InputItemsListResponseBody.InputItem.InputMessage]
      inputMessage.role shouldBe "user"

      // Validate that the input content contains our original text "Hi"
      val textContent = inputMessage.content.find(
        _.isInstanceOf[sttp.openai.requests.responses.InputItemsListResponseBody.InputItem.InputContent.InputText]
      )
      textContent should be(defined)
      val inputText =
        textContent.get.asInstanceOf[sttp.openai.requests.responses.InputItemsListResponseBody.InputItem.InputContent.InputText]
      inputText.text shouldBe "Hi"

      // when - Step 4: Delete response
      val deleteResult = client.deleteModelResponse(createdResponse.id)

      // then - Validate deletion
      deleteResult should not be null
      deleteResult.deleted shouldBe true
      deleteResult.id shouldBe createdResponse.id
      deleteResult.`object` shouldBe "response.deleted"
      ()
    }

  "OpenAI Error Handling" should "throw AuthenticationException for invalid API key" in {
    // given
    val invalidClient = OpenAISyncClient("invalid-api-key")

    // when & then
    val exception = intercept[OpenAIException] {
      invalidClient.getModels
    }
    exception.message.getOrElse("") should include("API key")
    invalidClient.close()
    ()
  }

  it should "handle rate limiting gracefully" in
    withClient { client =>
      // given
      // This test verifies that the library properly handles rate limit responses
      // We'll make multiple rapid requests to potentially trigger rate limiting
      val chatBody = ChatBody(
        model = ChatCompletionModel.GPT4oMini,
        messages = Seq(Message.UserMessage(Content.TextContent("Hi"))),
        maxTokens = Some(1) // Minimal to reduce cost
      )

      // when & then
      // Make several requests rapidly - if rate limited, should get proper exception
      val results = (1 to 3).map { _ =>
        Try(client.createChatCompletion(chatBody))
      }

      // At least some requests should succeed
      results.count(_.isSuccess) should be >= 1

      // If any failed, they should fail with proper OpenAI exceptions
      results.collect { case Failure(ex) => ex }.foreach {
        case _: OpenAIException => // Expected - this is what we want to test
        case other              => fail(s"Unexpected exception type: ${other.getClass.getSimpleName}")
      }
    }

  "OpenAI Client Integration" should "work with custom request modifications" in
    withClient { client =>
      // given
      val customClient = client.customizeRequest(new sttp.openai.CustomizeOpenAIRequest {
        override def apply[A](request: sttp.client4.Request[Either[OpenAIException, A]]): sttp.client4.Request[Either[OpenAIException, A]] =
          request.header("X-Custom-Header", "integration-test")
      })

      // when
      val models = customClient.getModels

      // then
      models should not be null
      models.data should not be empty
      // The custom header should not interfere with the request
      ()
    }
}
