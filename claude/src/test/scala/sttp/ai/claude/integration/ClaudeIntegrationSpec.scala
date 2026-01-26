package sttp.ai.claude.integration

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import sttp.ai.claude.ClaudeExceptions.{ClaudeException, UnsupportedModelForStructuredOutputException}
import sttp.ai.claude.ClaudeSyncClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, Message, OutputFormat, PropertySchema, Tool, ToolInputSchema}
import sttp.ai.claude.requests.MessageRequest
import sttp.model.Uri
import sttp.tapir.{Schema => TSchema}
import upickle.legacy.{macroRW, read, ReadWriter}

import scala.util.{Failure, Try}

case class Person(name: String, age: Int)

object Person {
  implicit val rw: ReadWriter[Person] = macroRW
  implicit val schema: TSchema[Person] = TSchema.derived[Person]
}

/** Integration tests for sttp-claude library that test against the real Claude API.
  *
  * These tests are designed to be cost-efficient by using minimal token usage.
  *
  * To run these tests, set the ANTHROPIC_API_KEY environment variable:
  * {{{
  * export ANTHROPIC_API_KEY=your-api-key-here
  * sbt "testOnly *ClaudeIntegrationSpec"
  * }}}
  *
  * If ANTHROPIC_API_KEY is not defined, all tests will be skipped (not failed).
  */
class ClaudeIntegrationSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll with Eventually {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(30, Seconds), interval = Span(500, Millis))

  private var clientOpt: Option[ClaudeSyncClient] = None
  private val maybeApiKey: Option[String] = sys.env.get("ANTHROPIC_API_KEY")

  override def beforeAll(): Unit = {
    super.beforeAll()
    maybeApiKey.foreach { apiKey =>
      val config = ClaudeConfig(
        apiKey = apiKey,
        baseUrl = Uri.unsafeParse("https://api.anthropic.com"),
        anthropicVersion = "2023-06-01"
      )
      clientOpt = Some(ClaudeSyncClient(config))
    }
  }

  override def afterAll(): Unit = {
    clientOpt.foreach(_.close())
    super.afterAll()
  }

  private def withClient[T](test: ClaudeSyncClient => T): T = {
    if (maybeApiKey.isEmpty) {
      cancel("ANTHROPIC_API_KEY not defined - skipping integration test")
    }
    clientOpt match {
      case Some(client) => test(client)
      case None         => fail("Claude client not initialized")
    }
  }

  "Claude Models API" should "list available models successfully" in
    withClient { client =>
      // given
      // No setup needed - getModels is typically a free endpoint

      // when
      val models = client.listModels()

      // then
      models should not be null
      models.data should not be empty
      models.data.foreach { model =>
        model.id should not be empty
        model.`type` should not be empty
        model.displayName should not be empty
      }
      ()
    }

  "Claude Messages API" should "create a simple message successfully" in
    withClient { client =>
      // given
      val request = MessageRequest.simple(
        model = "claude-3-haiku-20240307", // Using the cheapest Claude model
        messages = List(Message.user("Hi")), // Minimal message to reduce cost
        maxTokens = 5 // Limit tokens to minimize cost
      )

      // when
      val response = client.createMessage(request)

      // then
      response should not be null
      response.id should not be empty
      response.`type` shouldBe "message"
      response.role shouldBe "assistant"
      response.content should not be empty
      response.model should not be empty
      response.usage should not be null
      response.usage.inputTokens should be > 0
      response.usage.outputTokens should be > 0
      response.usage.inputTokens + response.usage.outputTokens should be <= 50 // Should be very low
      ()
    }

  it should "create a message with system prompt successfully" in
    withClient { client =>
      // given
      val request = MessageRequest.withSystem(
        model = "claude-3-haiku-20240307",
        system = "Be concise.", // Short system prompt
        messages = List(Message.user("What is 2+2?")), // Simple question
        maxTokens = 10
      )

      // when
      val response = client.createMessage(request)

      // then
      response should not be null
      response.role shouldBe "assistant"
      response.content should not be empty
      // The response should contain the answer to 2+2
      val textContent = response.content.collectFirst { case ContentBlock.TextContent(text) =>
        text
      }
      textContent should be(defined)
      textContent.get should include("4")
      ()
    }

  it should "handle image content successfully" in
    withClient { client =>
      // given
      // A minimal 1x1 pixel base64 PNG image for testing
      val minimalPngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="

      val imageMessage = Message.user(
        List(
          ContentBlock.TextContent("What do you see?"),
          ContentBlock.ImageContent(
            ContentBlock.ImageSource.base64("image/png", minimalPngBase64)
          )
        )
      )

      val request = MessageRequest.simple(
        model = "claude-3-haiku-20240307",
        messages = List(imageMessage),
        maxTokens = 20
      )

      // when
      val response = client.createMessage(request)

      // then
      response should not be null
      response.role shouldBe "assistant"
      response.content should not be empty
      // Claude should acknowledge the image in some way
      val textContent = response.content.collectFirst { case ContentBlock.TextContent(text) =>
        text
      }
      textContent should be(defined)
      textContent.get.toLowerCase should (include("image") or include("pixel") or include("see"))
      ()
    }

  it should "handle tool calling successfully" in
    withClient { client =>
      // given
      val weatherTool = Tool(
        name = "get_weather",
        description = "Get weather information for a city",
        inputSchema = ToolInputSchema.forObject(
          properties = Map(
            "city" -> PropertySchema.string("The city name")
          ),
          required = Some(List("city"))
        )
      )

      val request = MessageRequest.withTools(
        model = "claude-3-haiku-20240307",
        messages = List(Message.user("What's the weather in Paris?")),
        maxTokens = 50,
        tools = List(weatherTool)
      )

      // when
      val response = client.createMessage(request)

      // then
      response should not be null
      response.role shouldBe "assistant"
      response.content should not be empty

      // Claude should either use the tool or explain why it can't
      val hasToolUse = response.content.exists(_.isInstanceOf[ContentBlock.ToolUseContent])
      val hasTextResponse = response.content.exists {
        case ContentBlock.TextContent(text) => text.toLowerCase.contains("weather") || text.toLowerCase.contains("tool")
        case _                              => false
      }

      (hasToolUse || hasTextResponse) shouldBe true
      ()
    }

  "Claude Error Handling" should "throw AuthenticationException for invalid API key" in {
    // given
    val invalidConfig = ClaudeConfig(
      apiKey = "invalid-api-key",
      baseUrl = Uri.unsafeParse("https://api.anthropic.com"),
      anthropicVersion = "2023-06-01"
    )
    val invalidClient = ClaudeSyncClient(invalidConfig)

    // when & then
    val exception = intercept[ClaudeException] {
      invalidClient.listModels()
    }
    exception.message.getOrElse("") should include("authentication")
    invalidClient.close()
    ()
  }

  it should "handle rate limiting gracefully" in
    withClient { client =>
      // given
      val request = MessageRequest.simple(
        model = "claude-3-haiku-20240307",
        messages = List(Message.user("Hi")),
        maxTokens = 1 // Minimal to reduce cost
      )

      // when & then
      // Make several requests rapidly - if rate limited, should get proper exception
      val results = (1 to 3).map { _ =>
        Try(client.createMessage(request))
      }

      // At least some requests should succeed
      results.count(_.isSuccess) should be >= 1

      // If any failed, they should fail with proper Claude exceptions
      results.collect { case Failure(ex) => ex }.foreach {
        case _: ClaudeException => // Expected - this is what we want to test
        case other              => fail(s"Unexpected exception type: ${other.getClass.getSimpleName}")
      }
    }

  it should "handle invalid model name properly" in
    withClient { client =>
      // given
      val request = MessageRequest.simple(
        model = "non-existent-model",
        messages = List(Message.user("Test")),
        maxTokens = 5
      )

      // when & then
      val exception = intercept[ClaudeException] {
        client.createMessage(request)
      }
      exception.message.getOrElse("") should (include("model") or include("invalid"))
      ()
    }

  "Claude Tool Results" should "handle tool result messages" in
    withClient { client =>
      // Given a simple conversation to test message handling
      // This tests basic message structure rather than complex tool flows

      val request = MessageRequest.simple(
        model = "claude-3-haiku-20240307",
        messages = List(Message.user("Say 'Hello world' in exactly two words.")),
        maxTokens = 10
      )

      // when
      val response = client.createMessage(request)

      // then
      response should not be null
      response.role shouldBe "assistant"
      response.content should not be empty
      // Claude should respond with some text content
      val textContent = response.content.collectFirst { case ContentBlock.TextContent(text) =>
        text
      }
      textContent should be(defined)
      textContent.get should not be empty
      ()
    }

  "Claude Structured Output" should "return valid JSON matching the schema" in
    withClient { client =>
      // given
      val outputFormat = OutputFormat.JsonSchema.withTapirSchema[Person]

      val request = MessageRequest
        .simple("claude-haiku-4-5-20251001", List(Message.user("Generate a person named Alice who is 30 years old.")), 100)
        .withStructuredOutput(outputFormat)

      // when
      val response = client.createMessage(request)

      // then
      response should not be null
      response.role shouldBe "assistant"
      response.content should not be empty

      // Extract the text content which should be valid JSON
      val textContent = response.content.collectFirst { case ContentBlock.TextContent(text) =>
        text
      }
      textContent should be(defined)

      // Parse and validate the JSON response using the case class and uPickle
      val person = read[Person](textContent.get)
      person.name shouldBe "Alice"
      person.age shouldBe 30
      ()
    }

  it should "throw UnsupportedModelForStructuredOutputException for legacy models" in
    withClient { _ =>
      val outputFormat = OutputFormat.JsonSchema.withTapirSchema[Person]

      val exception = intercept[UnsupportedModelForStructuredOutputException] {
        val client = ClaudeSyncClient.fromEnv
        try {
          val request = MessageRequest
            .simple("claude-3-haiku-20240307", List(Message.user("Test")), 10)
            .withStructuredOutput(outputFormat)

          client.createMessage(request)
        } finally client.close()
      }

      exception.getMessage should include("claude-3-haiku-20240307")
      exception.getMessage should include("does not support structured output")
      ()
    }
}
