package sttp.ai.claude.streaming.ox

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.supervised
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, Message, PropertySchema, Tool, ToolInputSchema}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.MessageStreamResponse
import sttp.ai.claude.responses.MessageStreamResponse.ContentDelta
import sttp.client4.DefaultSyncBackend
import sttp.model.Uri

/** Integration test for Claude citation streaming via the Ox effect system.
  *
  * Requires ANTHROPIC_API_KEY to run:
  * {{{
  * export ANTHROPIC_API_KEY=your-api-key-here
  * sbt "testOnly *ClaudeOxStreamingIntegrationSpec"
  * }}}
  */
class ClaudeOxStreamingIntegrationSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private val testModel = "claude-haiku-4-5-20251001"

  private val maybeApiKey: Option[String] = sys.env.get("ANTHROPIC_API_KEY")

  private val docText =
    """The Messages API accepts a structured list of input messages with text and/or image content,
      |and the model will generate the next message in the conversation.
      |
      |Each input message must be an object with a role and content. You can specify a single
      |user-role message, or include multiple user and assistant messages. The first message must
      |always use the user role.
      |
      |The maximum number of tokens to generate before stopping is controlled by the max_tokens
      |parameter. Note that the model may stop before reaching this maximum.""".stripMargin

  private def withClient[T](test: ClaudeClient => T): T = {
    if (maybeApiKey.isEmpty) {
      cancel("ANTHROPIC_API_KEY not defined - skipping integration test")
    }
    val config = ClaudeConfig(
      apiKey = maybeApiKey.get,
      baseUrl = Uri.unsafeParse("https://api.anthropic.com"),
      anthropicVersion = "2023-06-01"
    )
    test(ClaudeClient(config))
  }

  "Claude Tool Calling streaming" should "deliver tool call with arguments via input_json_delta events" in
    withClient { client =>
      val weatherTool = Tool(
        name = "get_weather",
        description = "Get current weather for a city",
        inputSchema = ToolInputSchema.forObject(
          properties = Map(
            "city" -> PropertySchema.string("The city name"),
            "units" -> PropertySchema.string("Temperature units: celsius or fahrenheit")
          ),
          required = Some(List("city", "units"))
        )
      )

      val request = MessageRequest.withTools(
        model = testModel,
        messages = List(Message.user("What is the weather in Warsaw in celsius?")),
        maxTokens = 200,
        tools = List(weatherTool)
      )

      val backend = DefaultSyncBackend()
      try
        supervised {
          val events = client
            .createStreamedMessage(request)
            .send(backend)
            .body
            .getOrElse(fail("Expected successful streaming response"))
            .runToList()

          val deltas = events.collect { case Right(MessageStreamResponse.ContentBlockDelta(_, delta)) => delta }

          // Claude must stream tool input as input_json_delta chunks
          val jsonChunks = deltas.collect { case ContentDelta.InputJsonDelta(partial) => partial }
          jsonChunks should not be empty

          // Accumulated JSON must contain the expected arguments
          val fullJson = jsonChunks.mkString
          fullJson should include("Warsaw")
          fullJson should include("celsius")
        }
      finally backend.close()
    }

  "Claude Citations streaming" should "emit citations_delta events when summarising a provided document" in
    withClient { client =>
      val request = MessageRequest.simple(
        model = testModel,
        messages = List(
          Message.user(
            List(
              ContentBlock.document(docText, title = Some("Anthropic Messages API Documentation")),
              ContentBlock.text("Summarise this document in one sentence, citing specific claims.")
            )
          )
        ),
        maxTokens = 150
      )

      val backend = DefaultSyncBackend()
      try
        supervised {
          val events = client
            .createStreamedMessage(request)
            .send(backend)
            .body
            .getOrElse(fail("Expected successful streaming response"))
            .runToList()

          val deltas = events.collect { case Right(MessageStreamResponse.ContentBlockDelta(_, delta)) => delta }
          deltas should not be empty

          val hasCitationsDelta = deltas.exists(_.isInstanceOf[ContentDelta.CitationsDelta])
          val hasTextDelta = deltas.exists(_.isInstanceOf[ContentDelta.TextDelta])

          // The response must contain text content; citations_delta indicates the model cited the document
          hasTextDelta shouldBe true
          hasCitationsDelta shouldBe true
          events should not be empty

        }
      finally backend.close()
    }
}
