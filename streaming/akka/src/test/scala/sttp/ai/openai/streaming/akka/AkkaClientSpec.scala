package sttp.ai.openai.streaming.akka

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.util.ByteString
import org.scalatest.EitherValues
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.akkahttp.AkkaHttpBackend
import sttp.client4.testing.ResponseStub
import sttp.model.sse.ServerSentEvent
import sttp.ai.openai.OpenAI
import sttp.ai.openai.OpenAIExceptions.OpenAIException.DeserializationOpenAIException
import sttp.ai.openai.fixtures.ErrorFixture
import io.circe.parser.decode
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.requests.audio.speech.SpeechModel.TTS1
import sttp.ai.openai.requests.audio.speech.{SpeechRequestBody, Voice}
import sttp.ai.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse
import sttp.ai.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse.DoneEvent
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.fixtures.ResponsesStreamEventFixture
import sttp.ai.openai.requests.responses.ResponsesModel.GPT4oMini
import sttp.ai.openai.requests.responses.{GetResponseQueryParameters, ResponsesRequestBody, ResponsesStreamEvent}
import sttp.client4.{GenericRequest, StringBody}
import io.circe.parser.parse

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future

class AkkaClientSpec extends AsyncFlatSpec with Matchers with EitherValues {
  implicit val system: ActorSystem = ActorSystem()

  "Creating speech" should "return byte stream" in {
    // given
    val expectedResponse = "audio content"
    val akkaBackendStub = AkkaHttpBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(Source(ByteString(expectedResponse))))
    val client = new OpenAI(authToken = "test-token")
    val givenRequest = SpeechRequestBody(
      model = TTS1,
      input = "Hello, my name is John.",
      voice = Voice.Alloy
    )
    // when
    val response = client
      .createSpeech(givenRequest)
      .send(akkaBackendStub)
      .map(_.body.value)
      .flatMap(_.runWith(Sink.seq))
    // then
    response.map(_ shouldBe expectedResponse.getBytes.toSeq)
  }

  for ((statusCode, expectedError) <- ErrorFixture.testData)
    s"Service response with status code: $statusCode" should s"return properly deserialized ${expectedError.getClass.getSimpleName}" in {
      // given
      val akkaBackendStub = AkkaHttpBackend.stub.whenAnyRequest.thenRespondAdjust(ErrorFixture.errorResponse, statusCode)
      val client = new OpenAI("test-token")

      val givenRequest = ChatBody(
        model = ChatCompletionModel.GPT35Turbo,
        messages = Seq.empty
      )

      // when
      val caught = client
        .createStreamedChatCompletion(givenRequest)
        .send(akkaBackendStub)
        .map(_.body.left.value)

      // then
      caught.map { c =>
        c.getClass shouldBe expectedError.getClass
        c.message shouldBe expectedError.message
        c.cause.getClass shouldBe expectedError.cause.getClass
        c.code shouldBe expectedError.code
        c.param shouldBe expectedError.param
        c.`type` shouldBe expectedError.`type`
      }
    }

  "Creating chat completions with failed stream due to invalid deserialization" should "return properly deserialized error" in {
    // given
    val invalidJson = Some("invalid json")
    val invalidEvent = ServerSentEvent(invalidJson)

    val streamedResponse = Source
      .single(invalidEvent.toString)
      .map(ByteString(_))

    val akkaBackendStub = AkkaHttpBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(streamedResponse))
    val client = new OpenAI(authToken = "test-token")

    val givenRequest = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = Seq.empty
    )

    // when
    val response = client
      .createStreamedChatCompletion(givenRequest)
      .send(akkaBackendStub)
      .map(_.body.value)
      .flatMap(_.run())

    // then
    response.failed.map(_ shouldBe a[DeserializationOpenAIException])
  }

  "Creating chat completions with successful response" should "ignore empty events and return properly deserialized list of chunks" in {
    // given
    val chatChunks = Seq.fill(3)(sttp.ai.openai.fixtures.ChatChunkFixture.jsonResponse).map(s => parse(s).value.noSpaces)

    val eventsToProcess = chatChunks.map(data => ServerSentEvent(Some(data)))
    val emptyEvent = ServerSentEvent()
    val events = (eventsToProcess :+ emptyEvent) :+ DoneEvent

    val delimiter = "\n\n"
    val streamedResponse = Source(events)
      .map(_.toString + delimiter)
      .map(ByteString(_))

    // when & then
    assertStreamedCompletion(streamedResponse, chatChunks.map(decode[ChatChunkResponse](_).value))
  }

  "Creating chat completions with successful response" should "stop listening after [DONE] event and return properly deserialized list of chunks" in {
    // given
    val chatChunks = Seq.fill(3)(sttp.ai.openai.fixtures.ChatChunkFixture.jsonResponse).map(s => parse(s).value.noSpaces)

    val eventsToProcess = chatChunks.map(data => ServerSentEvent(Some(data)))
    val events = (eventsToProcess :+ DoneEvent) ++ eventsToProcess

    val delimiter = "\n\n"
    val streamedResponse = Source(events)
      .map(_.toString + delimiter)
      .map(ByteString(_))

    // when & then
    assertStreamedCompletion(streamedResponse, chatChunks.map(decode[ChatChunkResponse](_).value))
  }

  private def assertStreamedCompletion(givenResponse: Source[ByteString, NotUsed], expectedResponse: Seq[ChatChunkResponse]) = {
    val akkaBackendStub = AkkaHttpBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(givenResponse))
    val client = new OpenAI(authToken = "test-token")

    val givenRequest = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = Seq.empty
    )

    // when
    val response = client
      .createStreamedChatCompletion(givenRequest)
      .send(akkaBackendStub)
      .map(_.body.value)
      .flatMap(_.runWith(Sink.seq))

    // then
    response.map(_ shouldBe expectedResponse)
  }

  private val givenResponsesRequest = ResponsesRequestBody(model = Some(GPT4oMini), input = Some(Left("Hello!")))

  /** The Responses API sets an `event:` line on every frame, unlike chat completions - so the specs below populate `eventType`. */
  private def responsesEvents(payloads: Seq[String]): Seq[ServerSentEvent] =
    payloads.map(data => ServerSentEvent(Some(data), parse(data).value.hcursor.get[String]("type").toOption))

  private val doneEvent = ServerSentEvent(Some(ResponsesStreamEvent.DoneEventMessage))

  private def sseBytes(events: Seq[ServerSentEvent]): Source[ByteString, NotUsed] =
    Source(events.toList).map(_.toString + "\n\n").map(ByteString(_))

  for ((statusCode, expectedError) <- ErrorFixture.testData)
    s"Streamed model response with status code: $statusCode" should
      s"return properly deserialized ${expectedError.getClass.getSimpleName}" in {
        // given
        val akkaBackendStub = AkkaHttpBackend.stub.whenAnyRequest.thenRespondAdjust(ErrorFixture.errorResponse, statusCode)
        val client = new OpenAI("test-token")

        // when
        val caught = client
          .createStreamedModelResponse(givenResponsesRequest)
          .send(akkaBackendStub)
          .map(_.body.left.value)

        // then
        caught.map { c =>
          c.getClass shouldBe expectedError.getClass
          c.message shouldBe expectedError.message
          c.cause.getClass shouldBe expectedError.cause.getClass
          c.code shouldBe expectedError.code
          c.param shouldBe expectedError.param
          c.`type` shouldBe expectedError.`type`
        }
      }

  "Creating a streamed model response with failed stream due to invalid deserialization" should "return properly deserialized error" in {
    // given
    val akkaBackendStub =
      AkkaHttpBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(sseBytes(Seq(ServerSentEvent(Some("invalid json"))))))
    val client = new OpenAI(authToken = "test-token")

    // when
    val response = client
      .createStreamedModelResponse(givenResponsesRequest)
      .send(akkaBackendStub)
      .map(_.body.value)
      .flatMap(_.run())

    // then
    response.failed.map(_ shouldBe a[DeserializationOpenAIException])
  }

  "Creating a streamed model response" should "ignore empty events and return properly deserialized events" in {
    // given
    val payloads = ResponsesStreamEventFixture.sseSequence.map(s => parse(s).value.noSpaces)
    val events = (responsesEvents(payloads) :+ ServerSentEvent()) :+ doneEvent

    // when & then
    assertStreamedModelResponse(sseBytes(events), payloads.map(decode[ResponsesStreamEvent](_).fold(throw _, identity)))
  }

  // Unlike the chat modules, the [DONE] sentinel is *skipped* rather than treated as end-of-stream: the Responses API terminates with
  // `response.completed` and then closes the connection, so a sentinel from an OpenAI-compatible provider must not truncate the stream.
  it should "skip the [DONE] sentinel without truncating the stream" in {
    // given
    val payloads = ResponsesStreamEventFixture.sseSequence.map(s => parse(s).value.noSpaces)
    val (before, after) = responsesEvents(payloads).splitAt(2)
    val events = (before :+ doneEvent) ++ after

    // when & then
    assertStreamedModelResponse(sseBytes(events), payloads.map(decode[ResponsesStreamEvent](_).fold(throw _, identity)))
  }

  "createStreamedModelResponse" should "send stream = true in the request body" in {
    // given
    val capturedRequest = new AtomicReference[GenericRequest[_, _]](null)
    val akkaBackendStub = AkkaHttpBackend.stub.whenAnyRequest.thenRespondF { request =>
      capturedRequest.set(request)
      Future.successful(ResponseStub.adjust(sseBytes(Seq(doneEvent))))
    }
    val client = new OpenAI(authToken = "test-token")

    // when
    val response = client
      .createStreamedModelResponse(givenResponsesRequest.copy(stream = Some(false)))
      .send(akkaBackendStub)
      .map(_.body.value)
      .flatMap(_.runWith(Sink.seq))

    // then
    response.map { events =>
      events shouldBe empty
      val requestBody = capturedRequest.get().body.asInstanceOf[StringBody].s
      parse(requestBody).value.hcursor.get[Boolean]("stream").value shouldBe true
    }
  }

  "resumeStreamedModelResponse" should "request the stored response with stream = true and starting_after" in {
    // given
    val capturedRequest = new AtomicReference[GenericRequest[_, _]](null)
    val akkaBackendStub = AkkaHttpBackend.stub.whenAnyRequest.thenRespondF { request =>
      capturedRequest.set(request)
      Future.successful(ResponseStub.adjust(sseBytes(Seq(doneEvent))))
    }
    val client = new OpenAI(authToken = "test-token")

    // when
    val response = client
      .resumeStreamedModelResponse("resp_123", GetResponseQueryParameters(startingAfter = Some(7)))
      .send(akkaBackendStub)
      .map(_.body.value)
      .flatMap(_.run())

    // then
    response.map { _ =>
      val uri = capturedRequest.get().uri
      uri.path should contain("resp_123")
      uri.params.get("stream") shouldBe Some("true")
      uri.params.get("starting_after") shouldBe Some("7")
    }
  }

  private def assertStreamedModelResponse(
      givenResponse: Source[ByteString, NotUsed],
      expectedResponse: Seq[ResponsesStreamEvent]
  ) = {
    val akkaBackendStub = AkkaHttpBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(givenResponse))
    val client = new OpenAI(authToken = "test-token")

    // when
    val response = client
      .createStreamedModelResponse(givenResponsesRequest)
      .send(akkaBackendStub)
      .map(_.body.value)
      .flatMap(_.runWith(Sink.seq))

    // then
    response.map(_ shouldBe expectedResponse)
  }
}
