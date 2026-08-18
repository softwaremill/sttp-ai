package sttp.ai.openai.streaming.ox

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.{supervised, Ox}
import sttp.client4.DefaultSyncBackend
import sttp.client4.testing.ResponseStub
import sttp.model.sse.ServerSentEvent
import sttp.ai.openai.OpenAI
import sttp.ai.openai.OpenAIExceptions.OpenAIException.DeserializationOpenAIException
import sttp.ai.openai.fixtures.ErrorFixture
import io.circe.parser.decode
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse
import sttp.ai.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse.DoneEvent
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.fixtures.ResponsesStreamEventFixture
import sttp.ai.openai.requests.responses.ResponsesModel.GPT4oMini
import sttp.ai.openai.requests.responses.{GetResponseQueryParameters, ResponsesRequestBody, ResponsesStreamEvent}
import sttp.client4.{GenericRequest, StringBody}
import io.circe.parser.parse

import java.io.{ByteArrayInputStream, InputStream}
import java.util.concurrent.atomic.AtomicReference

class OxClientSpec extends AnyFlatSpec with Matchers with EitherValues {
  for ((statusCode, expectedError) <- ErrorFixture.testData)
    s"Service response with status code: $statusCode" should s"return properly deserialized ${expectedError.getClass.getSimpleName}" in {
      // given
      val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(ErrorFixture.errorResponse, statusCode)
      val client = new OpenAI("test-token")

      val givenRequest = ChatBody(
        model = ChatCompletionModel.GPT35Turbo,
        messages = Seq.empty
      )

      // when
      val caught = client
        .createStreamedChatCompletion(givenRequest)
        .send(stub)
        .body
        .left
        .value

      // then
      caught.getClass shouldBe expectedError.getClass
      caught.message shouldBe expectedError.message
      caught.cause.getClass shouldBe expectedError.cause.getClass
      caught.code shouldBe expectedError.code
      caught.param shouldBe expectedError.param
      caught.`type` shouldBe expectedError.`type`
    }

  "Creating chat completions with failed stream due to invalid deserialization" should "return properly deserialized error" in {
    // given
    val invalidJson = Some("invalid json")
    val invalidEvent = ServerSentEvent(invalidJson)

    val streamedResponse = new ByteArrayInputStream(invalidEvent.toString.getBytes)

    val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(streamedResponse))
    val client = new OpenAI(authToken = "test-token")

    val givenRequest = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = Seq.empty
    )

    // when
    supervised {
      val response = client
        .createStreamedChatCompletion(givenRequest)
        .send(stub)
        .body
        .value
        .runToList()

      // then
      response(0) shouldBe a[Left[DeserializationOpenAIException, Any]]
    }
  }

  "Creating chat completions with successful response" should "ignore empty events and return properly deserialized list of chunks" in {
    // given
    val chatChunks = Seq.fill(3)(sttp.ai.openai.fixtures.ChatChunkFixture.jsonResponse).map(s => parse(s).value.noSpaces)

    val eventsToProcess = chatChunks.map(data => ServerSentEvent(Some(data)))
    val emptyEvent = ServerSentEvent()
    val events = (eventsToProcess :+ emptyEvent) :+ DoneEvent

    val delimiter = "\n\n"
    supervised {
      val streamedResponse = new ByteArrayInputStream(
        events
          .map(_.toString + delimiter)
          .flatMap(_.getBytes)
          .toArray
      )

      // when & then
      assertStreamedCompletion(streamedResponse, chatChunks.map(decode[ChatChunkResponse](_).fold(throw _, identity)))
    }
  }

  "Creating chat completions with successful response" should "stop listening after [DONE] event and return properly deserialized list of chunks" in {
    // given
    val chatChunks = Seq.fill(3)(sttp.ai.openai.fixtures.ChatChunkFixture.jsonResponse).map(s => parse(s).value.noSpaces)

    val eventsToProcess = chatChunks.map(data => ServerSentEvent(Some(data)))
    val events = (eventsToProcess :+ DoneEvent) ++ eventsToProcess

    val delimiter = "\n\n"
    supervised {
      val streamedResponse = new ByteArrayInputStream(
        events
          .map(_.toString + delimiter)
          .flatMap(_.getBytes)
          .toArray
      )

      // when & then
      assertStreamedCompletion(streamedResponse, chatChunks.map(decode[ChatChunkResponse](_).fold(throw _, identity)))
    }
  }

  private def assertStreamedCompletion(givenResponse: InputStream, expectedResponse: Seq[ChatChunkResponse])(using Ox) = {
    val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(givenResponse))
    val client = new OpenAI(authToken = "test-token")

    val givenRequest = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = Seq.empty
    )

    // when
    val response = client
      .createStreamedChatCompletion(givenRequest)
      .send(stub)
      .body
      .value
      .runToList()
      .map(_.value)

    // then
    response shouldBe expectedResponse
  }

  private val givenResponsesRequest = ResponsesRequestBody(model = Some(GPT4oMini), input = Some(Left("Hello!")))

  /** The Responses API sets an `event:` line on every frame, unlike chat completions - so the specs below populate `eventType`. */
  private def responsesEvents(payloads: Seq[String]): Seq[ServerSentEvent] =
    payloads.map(data => ServerSentEvent(Some(data), parse(data).value.hcursor.get[String]("type").toOption))

  private val doneEvent = ServerSentEvent(Some(ResponsesStreamEvent.DoneEventMessage))

  private def sseStream(events: Seq[ServerSentEvent]): InputStream =
    new ByteArrayInputStream(events.map(_.toString + "\n\n").flatMap(_.getBytes).toArray)

  for ((statusCode, expectedError) <- ErrorFixture.testData)
    s"Streamed model response with status code: $statusCode" should
      s"return properly deserialized ${expectedError.getClass.getSimpleName}" in {
        // given
        val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(ErrorFixture.errorResponse, statusCode)
        val client = new OpenAI("test-token")

        // when
        val caught = client
          .createStreamedModelResponse(givenResponsesRequest)
          .send(stub)
          .body
          .left
          .value

        // then
        caught.getClass shouldBe expectedError.getClass
        caught.message shouldBe expectedError.message
        caught.cause.getClass shouldBe expectedError.cause.getClass
        caught.code shouldBe expectedError.code
        caught.param shouldBe expectedError.param
        caught.`type` shouldBe expectedError.`type`
      }

  "Creating a streamed model response with failed stream due to invalid deserialization" should "return properly deserialized error" in {
    // given
    val stub =
      DefaultSyncBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(sseStream(Seq(ServerSentEvent(Some("invalid json"))))))
    val client = new OpenAI(authToken = "test-token")

    // when
    supervised {
      val response = client
        .createStreamedModelResponse(givenResponsesRequest)
        .send(stub)
        .body
        .value
        .runToList()

      // then
      response(0) shouldBe a[Left[DeserializationOpenAIException, Any]]
    }
  }

  "Creating a streamed model response" should "ignore empty events and return properly deserialized events" in {
    // given
    val payloads = ResponsesStreamEventFixture.sseSequence.map(s => parse(s).value.noSpaces)
    val events = (responsesEvents(payloads) :+ ServerSentEvent()) :+ doneEvent

    supervised {
      // when & then
      assertStreamedModelResponse(sseStream(events), payloads.map(decode[ResponsesStreamEvent](_).fold(throw _, identity)))
    }
  }

  // Unlike the chat modules, the [DONE] sentinel is *skipped* rather than treated as end-of-stream: the Responses API terminates with
  // `response.completed` and then closes the connection, so a sentinel from an OpenAI-compatible provider must not truncate the stream.
  it should "skip the [DONE] sentinel without truncating the stream" in {
    // given
    val payloads = ResponsesStreamEventFixture.sseSequence.map(s => parse(s).value.noSpaces)
    val (before, after) = responsesEvents(payloads).splitAt(2)
    val events = (before :+ doneEvent) ++ after

    supervised {
      // when & then
      assertStreamedModelResponse(sseStream(events), payloads.map(decode[ResponsesStreamEvent](_).fold(throw _, identity)))
    }
  }

  "createStreamedModelResponse" should "send stream = true in the request body" in {
    // given
    val capturedRequest = new AtomicReference[GenericRequest[_, _]](null)
    val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { request =>
      capturedRequest.set(request)
      ResponseStub.adjust(sseStream(Seq(doneEvent)))
    }
    val client = new OpenAI(authToken = "test-token")

    // when
    supervised {
      val response = client
        .createStreamedModelResponse(givenResponsesRequest.copy(stream = Some(false)))
        .send(stub)
        .body
        .value
        .runToList()

      // then
      response shouldBe empty
    }
    val requestBody = capturedRequest.get().body.asInstanceOf[StringBody].s
    parse(requestBody).value.hcursor.get[Boolean]("stream").value shouldBe true
  }

  "resumeStreamedModelResponse" should "request the stored response with stream = true and starting_after" in {
    // given
    val capturedRequest = new AtomicReference[GenericRequest[_, _]](null)
    val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { request =>
      capturedRequest.set(request)
      ResponseStub.adjust(sseStream(Seq(doneEvent)))
    }
    val client = new OpenAI(authToken = "test-token")

    // when
    supervised {
      client
        .resumeStreamedModelResponse("resp_123", GetResponseQueryParameters(startingAfter = Some(7)))
        .send(stub)
        .body
        .value
        .runToList(): Unit
    }

    // then
    val uri = capturedRequest.get().uri
    uri.path should contain("resp_123")
    uri.params.get("stream") shouldBe Some("true")
    uri.params.get("starting_after") shouldBe Some("7")
  }

  private def assertStreamedModelResponse(givenResponse: InputStream, expectedResponse: Seq[ResponsesStreamEvent])(using Ox) = {
    val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(givenResponse))
    val client = new OpenAI(authToken = "test-token")

    // when
    val response = client
      .createStreamedModelResponse(givenResponsesRequest)
      .send(stub)
      .body
      .value
      .runToList()
      .map(_.value)

    // then
    response shouldBe expectedResponse
  }
}
