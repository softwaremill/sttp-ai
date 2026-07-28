package sttp.ai.gemini.streaming.fs2

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import fs2.{text, Stream}
import org.scalatest.EitherValues
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.GeminiExceptions.GeminiException.DeserializationGeminiException
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.responses.InteractionStreamEvent
import sttp.ai.gemini.responses.InteractionStreamEvent.DoneEvent
import sttp.ai.gemini.streaming.fs2.GeminiFs2Streaming._
import io.circe.parser.decode
import sttp.ai.gemini.json.GeminiDerivedCodecs._
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.client4.testing.ResponseStub
import sttp.model.StatusCode
import sttp.model.StatusCode._
import sttp.model.sse.ServerSentEvent

class GeminiFs2ClientSpec extends AsyncFlatSpec with AsyncIOSpec with Matchers with EitherValues {

  private val errorMessage = "Some error message."

  private def errorResponse(message: String, status: String): String =
    s"""{
       |  "error": {
       |    "code": "$status",
       |    "message": "$message",
       |    "status": "$status"
       |  }
       |}""".stripMargin

  private val testData: Seq[(StatusCode, String, Class[_ <: GeminiException])] = List(
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
      val fs2BackendStub = HttpClientFs2Backend.stub[IO].whenAnyRequest.thenRespondAdjust(errorJson, statusCode)
      val client = GeminiClient(GeminiConfig("test-token"))

      val givenRequest = InteractionRequest.simple("gemini-3.5-flash-lite", "Hello")

      // when
      val caught = client
        .createStreamedInteraction[IO](givenRequest)
        .send(fs2BackendStub)
        .map(_.body.left.value)

      // then
      caught.asserting(_.getClass shouldBe expectedErrorClass)
    }

  "Creating interactions with failed stream due to invalid deserialization" should "fail the stream with a deserialization error" in {
    // given
    val invalidJson = Some("invalid json")
    val invalidEvent = ServerSentEvent(invalidJson)

    val streamedResponse = Stream
      .emit(invalidEvent.toString)
      .through(text.utf8.encode)
      .covary[IO]

    val fs2BackendStub = HttpClientFs2Backend.stub[IO].whenAnyRequest.thenRespond(ResponseStub.adjust(streamedResponse))
    val client = GeminiClient(GeminiConfig("test-token"))

    val givenRequest = InteractionRequest.simple("gemini-3.5-flash-lite", "Hello")

    // when
    val response = client
      .createStreamedInteraction[IO](givenRequest)
      .send(fs2BackendStub)
      .map(_.body.value)
      .flatMap(_.compile.drain)

    // then
    response.attempt.asserting(_ shouldBe a[Left[DeserializationGeminiException, _]])
  }

  "Creating interactions with successful response" should "ignore empty events and return properly deserialized list of events" in {
    // given
    val interactionEvents = Seq.fill(3)(InteractionStreamFixture.completedEvent)

    val eventsToProcess = interactionEvents.map(data => ServerSentEvent(Some(data)))
    val emptyEvent = ServerSentEvent()
    val events = eventsToProcess :+ emptyEvent

    val delimiter = "\n\n"
    val streamedResponse = Stream
      .emits(events)
      .map(_.toString + delimiter)
      .through(text.utf8.encode)
      .covary[IO]

    // when & then
    assertStreamedInteraction(streamedResponse, interactionEvents.map(decode[InteractionStreamEvent](_).fold(throw _, identity)))
  }

  "Creating interactions with successful response" should "filter out [DONE] events and return properly deserialized list of events" in {
    // given
    val interactionEvents = Seq.fill(3)(InteractionStreamFixture.completedEvent)

    val eventsToProcess = interactionEvents.map(data => ServerSentEvent(Some(data)))
    val doneEvent = ServerSentEvent(Some(DoneEvent))
    val events = eventsToProcess :+ doneEvent

    val delimiter = "\n\n"
    val streamedResponse = Stream
      .emits(events)
      .map(_.toString + delimiter)
      .through(text.utf8.encode)
      .covary[IO]

    // when & then
    assertStreamedInteraction(streamedResponse, interactionEvents.map(decode[InteractionStreamEvent](_).fold(throw _, identity)))
  }

  private def assertStreamedInteraction(givenResponse: Stream[IO, Byte], expectedResponse: Seq[InteractionStreamEvent]) = {
    val fs2BackendStub = HttpClientFs2Backend.stub[IO].whenAnyRequest.thenRespond(ResponseStub.adjust(givenResponse))
    val client = GeminiClient(GeminiConfig("test-token"))

    val givenRequest = InteractionRequest.simple("gemini-3.5-flash-lite", "Hello")

    // when
    val response = client
      .createStreamedInteraction[IO](givenRequest)
      .send(fs2BackendStub)
      .map(_.body.value)
      .flatMap(_.compile.toList)

    // then
    response.asserting(_ shouldBe expectedResponse)
  }
}

object InteractionStreamFixture {
  val completedEvent: String =
    """{"event_type":"interaction.completed","interaction":{"id":"int_1","status":"completed"}}"""
}
