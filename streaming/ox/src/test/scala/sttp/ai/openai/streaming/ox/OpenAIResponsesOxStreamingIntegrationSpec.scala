package sttp.ai.openai.streaming.ox

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.supervised
import sttp.ai.openai.OpenAI
import sttp.ai.openai.requests.responses.ResponsesModel.GPT56Luna
import sttp.ai.openai.requests.responses.ResponsesResponseBody.OutputItem
import sttp.ai.openai.requests.responses.{ResponsesRequestBody, ResponsesStreamEvent}
import sttp.ai.openai.requests.responses.Tool
import sttp.client4.DefaultSyncBackend
import sttp.tapir.Schema

/** Integration test for streaming the OpenAI Responses API via the Ox effect system.
  *
  * Requires OPENAI_API_KEY to run:
  * {{{
  * export OPENAI_API_KEY=your-api-key-here
  * sbt "testOnly *OpenAIResponsesOxStreamingIntegrationSpec"
  * }}}
  */
class OpenAIResponsesOxStreamingIntegrationSpec extends AnyFlatSpec with Matchers {

  private val maybeApiKey: Option[String] = sys.env.get("OPENAI_API_KEY")

  private def withClient[T](test: OpenAI => T): T = {
    if (maybeApiKey.isEmpty) {
      cancel("OPENAI_API_KEY not defined - skipping integration test")
    }
    test(new OpenAI(maybeApiKey.get))
  }

  "Streaming a model response" should "deliver the lifecycle events and the output text deltas" in withClient { client =>
    val request = ResponsesRequestBody(
      model = Some(GPT56Luna),
      input = Some(Left("Say hello in exactly two words.")),
      maxOutputTokens = Some(16)
    )

    val backend = DefaultSyncBackend()
    try
      supervised {
        val events = client
          .createStreamedModelResponse(request)
          .send(backend)
          .body
          .getOrElse(fail("Expected a successful streaming response"))
          .runToList()
          .map(_.fold(e => fail(s"Failed to decode a stream event: ${e.getMessage}", e), identity))

        events should not be empty
        events.head shouldBe a[ResponsesStreamEvent.Created]
        events.last shouldBe a[ResponsesStreamEvent.Completed]

        // No event may fall through to Unknown: that would mean OpenAI ships an event type this release does not model.
        events.collect { case unknown: ResponsesStreamEvent.Unknown => unknown.`type` } shouldBe empty

        val text = events.collect { case delta: ResponsesStreamEvent.OutputTextDelta => delta.delta }.mkString
        text should not be empty

        // Sequence numbers must be delivered in order, so consumers can rely on them for resuming.
        val sequenceNumbers = events.map(_.sequenceNumber)
        sequenceNumbers shouldBe sequenceNumbers.sorted

        // Usage is reported on the terminal lifecycle event, reachable from any event via `usage`.
        val usage = events.flatMap(_.usage).lastOption.getOrElse(fail("Expected usage to be reported"))
        usage.inputTokens should be > 0
        usage.outputTokens should be > 0
        usage.totalTokens shouldBe usage.inputTokens + usage.outputTokens

        // Only the terminal event carries it.
        events.filter(_.usage.isDefined).map(_.`type`) shouldBe List("response.completed")
      }
    finally backend.close()
  }

  case class UniversalWeatherTool(
      city: String
  )

  object UniversalWeatherTool {
    implicit val schema: Schema[UniversalWeatherTool] = Schema.derived
  }

  it should "handle function tool calls" in withClient { client =>
    val request = ResponsesRequestBody(
      model = Some(GPT56Luna),
      input = Some(Left("Tell me the weather in Warsaw, use provided tool!")),
      tools = Some(
        List(
          Tool.Function.withTapirSchema[UniversalWeatherTool]("universal_weather_tool", Some("Provides current weather for a given city."))
        )
      ),
      // Enough for the reasoning tokens plus the tool call's arguments, while keeping this paid call cheap.
      maxOutputTokens = Some(256)
    )

    val backend = DefaultSyncBackend()
    try
      supervised {
        val events = client
          .createStreamedModelResponse(request)
          .send(backend)
          .body
          .getOrElse(fail("Expected a successful streaming response"))
          .runToList()
          .map(_.fold(e => fail(s"Failed to decode a stream event: ${e.getMessage}", e), identity))

        events should not be empty

        assertSequenceExists(events)(
          "ouput-item-added" -> {
            case ResponsesStreamEvent.OutputItemAdded(OutputItem.FunctionCall(_, _, "universal_weather_tool", _, _), _, _) =>
          },
          "function-delta" -> { case _: ResponsesStreamEvent.FunctionCallArgumentsDelta =>
          },
          "function-done" -> { case _: ResponsesStreamEvent.FunctionCallArgumentsDone =>
          },
          "output-item-done" -> {
            case ResponsesStreamEvent.OutputItemDone(OutputItem.FunctionCall(arguments, _, "universal_weather_tool", _, _), _, _)
                if arguments.nonEmpty =>

          }
        )
      }
    finally backend.close()
  }

  private def assertSequenceExists(events: Seq[ResponsesStreamEvent])(
      functions: (String, PartialFunction[ResponsesStreamEvent, Unit])*
  ) = {
    val unmatched = events
      .foldLeft(functions) { (leftToMatch, elem) =>
        if (leftToMatch.isEmpty) {
          leftToMatch
        } else {
          val (_, function) = leftToMatch.head
          if (function.isDefinedAt(elem)) {
            leftToMatch.tail
          } else {
            leftToMatch
          }
        }
      }

    unmatched shouldBe empty
  }
}
