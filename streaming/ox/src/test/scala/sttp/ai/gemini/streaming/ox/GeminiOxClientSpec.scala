package sttp.ai.gemini.streaming.ox

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.{supervised, Ox}
import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.GeminiExceptions.GeminiException.DeserializationGeminiException
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.responses.InteractionStreamEvent
import sttp.ai.gemini.responses.InteractionStreamEvent.DoneEvent
import io.circe.parser.decode
import sttp.ai.gemini.json.GeminiDerivedCodecs._
import sttp.client4.DefaultSyncBackend
import sttp.client4.testing.ResponseStub
import sttp.model.StatusCode
import sttp.model.StatusCode.*
import sttp.model.sse.ServerSentEvent

import java.io.{ByteArrayInputStream, InputStream}

class GeminiOxClientSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val errorMessage = "Some error message."

  private def errorResponse(message: String, status: String): String =
    s"""{
       |  "error": {
       |    "code": "$status",
       |    "message": "$message",
       |    "status": "$status"
       |  }
       |}""".stripMargin

  private val testData: Seq[(StatusCode, String, Class[? <: GeminiException])] = List(
    (Unauthorized, "UNAUTHENTICATED", classOf[GeminiException.AuthenticationException]),
    (TooManyRequests, "RESOURCE_EXHAUSTED", classOf[GeminiException.RateLimitException]),
    (BadRequest, "INVALID_ARGUMENT", classOf[GeminiException.InvalidRequestException]),
    (NotFound, "NOT_FOUND", classOf[GeminiException.NotFoundException]),
    (ServiceUnavailable, "UNAVAILABLE", classOf[GeminiException.ServiceUnavailableException])
  )

  for ((statusCode, geminiStatus, expectedErrorClass) <- testData)
    s"Service response with status code: $statusCode" should s"return properly deserialized ${expectedErrorClass.getSimpleName}" in {
      // given
      val errorJson = errorResponse(errorMessage, geminiStatus)
      val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(errorJson, statusCode)
      val client = GeminiClient(GeminiConfig("test-token"))

      val givenRequest = InteractionRequest.simple("gemini-3.5-flash-lite", "Hello")

      // when
      val caught = client
        .createStreamedInteraction(givenRequest)
        .send(stub)
        .body
        .left
        .value

      // then
      caught.getClass shouldBe expectedErrorClass
    }

  "Creating interactions with failed stream due to invalid deserialization" should "return properly deserialized error" in {
    // given
    val invalidJson = Some("invalid json")
    val invalidEvent = ServerSentEvent(invalidJson)

    val streamedResponse = new ByteArrayInputStream(invalidEvent.toString.getBytes)

    val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(streamedResponse))
    val client = GeminiClient(GeminiConfig("test-token"))

    val givenRequest = InteractionRequest.simple("gemini-3.5-flash-lite", "Hello")

    // when
    supervised {
      val response = client
        .createStreamedInteraction(givenRequest)
        .send(stub)
        .body
        .value
        .runToList()

      // then
      response.head shouldBe a[Left[DeserializationGeminiException, Any]]
    }
  }

  "Creating interactions with successful response" should "ignore empty events and return properly deserialized list of events" in {
    // given
    val interactionEvents = Seq.fill(3)(InteractionStreamFixture.completedEvent)

    val eventsToProcess = interactionEvents.map(data => ServerSentEvent(Some(data)))
    val emptyEvent = ServerSentEvent()
    val events = eventsToProcess :+ emptyEvent

    val delimiter = "\n\n"
    supervised {
      val streamedResponse = new ByteArrayInputStream(
        events
          .map(_.toString + delimiter)
          .flatMap(_.getBytes)
          .toArray
      )

      // when & then
      assertStreamedInteraction(streamedResponse, interactionEvents.map(decode[InteractionStreamEvent](_).fold(throw _, identity)))
    }
  }

  "Creating interactions with successful response" should "filter out [DONE] events and return properly deserialized list of events" in {
    // given
    val interactionEvents = Seq.fill(3)(InteractionStreamFixture.completedEvent)

    val eventsToProcess = interactionEvents.map(data => ServerSentEvent(Some(data)))
    val doneEvent = ServerSentEvent(Some(DoneEvent))
    val events = eventsToProcess :+ doneEvent

    val delimiter = "\n\n"
    supervised {
      val streamedResponse = new ByteArrayInputStream(
        events
          .map(_.toString + delimiter)
          .flatMap(_.getBytes)
          .toArray
      )

      // when & then
      assertStreamedInteraction(streamedResponse, interactionEvents.map(decode[InteractionStreamEvent](_).fold(throw _, identity)))
    }
  }

  private def assertStreamedInteraction(givenResponse: InputStream, expectedResponse: Seq[InteractionStreamEvent])(using Ox) = {
    val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(givenResponse))
    val client = GeminiClient(GeminiConfig("test-token"))

    val givenRequest = InteractionRequest.simple("gemini-3.5-flash-lite", "Hello")

    // when
    val response = client
      .createStreamedInteraction(givenRequest)
      .send(stub)
      .body
      .value
      .runToList()
      .map(_.value)

    // then
    response shouldBe expectedResponse
  }
}

object InteractionStreamFixture {
  val completedEvent: String =
    """{"event_type":"interaction.completed","interaction":{"id":"int_1","status":"completed"}}"""
}
