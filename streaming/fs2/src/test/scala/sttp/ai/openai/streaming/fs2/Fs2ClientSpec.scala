package sttp.openai.streaming.fs2

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import fs2.{text, Stream}
import org.scalatest.EitherValues
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.client4.testing.ResponseStub
import sttp.model.sse.ServerSentEvent
import sttp.openai.OpenAI
import sttp.openai.OpenAIExceptions.OpenAIException.DeserializationOpenAIException
import sttp.openai.fixtures.ErrorFixture
import sttp.openai.json.SnakePickle._
import sttp.openai.requests.audio.speech.SpeechModel.TTS1
import sttp.openai.requests.audio.speech.{SpeechRequestBody, Voice}
import sttp.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse
import sttp.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse.DoneEvent
import sttp.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.openai.utils.JsonUtils.compactJson

class Fs2ClientSpec extends AsyncFlatSpec with AsyncIOSpec with Matchers with EitherValues {
  "Creating speech" should "return byte stream" in {
    // given
    val expectedResponse = "audio content"
    val streamedResponse = Stream.emit(expectedResponse).through(text.utf8.encode).covary[IO]
    val fs2BackendStub = HttpClientFs2Backend.stub[IO].whenAnyRequest.thenRespond(ResponseStub.adjust(streamedResponse))
    val client = new OpenAI(authToken = "test-token")
    val givenRequest = SpeechRequestBody(
      model = TTS1,
      input = "Hello, my name is John.",
      voice = Voice.Alloy
    )
    // when
    val response = client
      .createSpeech[IO](givenRequest)
      .send(fs2BackendStub)
      .map(_.body.value)
      .flatMap(_.compile.toList)
    // then
    response.asserting(_ shouldBe expectedResponse.getBytes.toSeq)
  }

  for ((statusCode, expectedError) <- ErrorFixture.testData)
    s"Service response with status code: $statusCode" should s"return properly deserialized ${expectedError.getClass.getSimpleName}" in {
      // given
      val fs2BackendStub = HttpClientFs2Backend.stub[IO].whenAnyRequest.thenRespondAdjust(ErrorFixture.errorResponse, statusCode)
      val client = new OpenAI("test-token")

      val givenRequest = ChatBody(
        model = ChatCompletionModel.GPT35Turbo,
        messages = Seq.empty
      )

      // when
      val caught = client
        .createStreamedChatCompletion[IO](givenRequest)
        .send(fs2BackendStub)
        .map(_.body.left.value)

      // then
      caught.asserting { c =>
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

    val streamedResponse = Stream
      .emit(invalidEvent.toString)
      .through(text.utf8.encode)
      .covary[IO]

    val fs2BackendStub = HttpClientFs2Backend.stub[IO].whenAnyRequest.thenRespond(ResponseStub.adjust(streamedResponse))
    val client = new OpenAI(authToken = "test-token")

    val givenRequest = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = Seq.empty
    )

    // when
    val response = client
      .createStreamedChatCompletion[IO](givenRequest)
      .send(fs2BackendStub)
      .map(_.body.value)
      .flatMap(_.compile.drain)

    // then
    response.attempt.asserting(_ shouldBe a[Left[DeserializationOpenAIException, _]])
  }

  "Creating chat completions with successful response" should "ignore empty events and return properly deserialized list of chunks" in {
    // given
    val chatChunks = Seq.fill(3)(sttp.openai.fixtures.ChatChunkFixture.jsonResponse).map(compactJson)

    val eventsToProcess = chatChunks.map(data => ServerSentEvent(Some(data)))
    val emptyEvent = ServerSentEvent()
    val events = (eventsToProcess :+ emptyEvent) :+ DoneEvent

    val delimiter = "\n\n"
    val streamedResponse = Stream
      .emits(events)
      .map(_.toString + delimiter)
      .through(text.utf8.encode)
      .covary[IO]

    // when & then
    assertStreamedCompletion(streamedResponse, chatChunks.map(read[ChatChunkResponse](_)))
  }

  "Creating chat completions with successful response" should "stop listening after [DONE] event and return properly deserialized list of chunks" in {
    // given
    val chatChunks = Seq.fill(3)(sttp.openai.fixtures.ChatChunkFixture.jsonResponse).map(compactJson)

    val eventsToProcess = chatChunks.map(data => ServerSentEvent(Some(data)))
    val events = (eventsToProcess :+ DoneEvent) ++ eventsToProcess

    val delimiter = "\n\n"
    val streamedResponse = Stream
      .emits(events)
      .map(_.toString + delimiter)
      .through(text.utf8.encode)
      .covary[IO]

    // when & then
    assertStreamedCompletion(streamedResponse, chatChunks.map(read[ChatChunkResponse](_)))
  }

  private def assertStreamedCompletion(givenResponse: Stream[IO, Byte], expectedResponse: Seq[ChatChunkResponse]) = {
    val pekkoBackendStub = HttpClientFs2Backend.stub[IO].whenAnyRequest.thenRespond(ResponseStub.adjust(givenResponse))
    val client = new OpenAI(authToken = "test-token")

    val givenRequest = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = Seq.empty
    )

    // when
    val response = client
      .createStreamedChatCompletion[IO](givenRequest)
      .send(pekkoBackendStub)
      .map(_.body.value)
      .flatMap(_.compile.toList)

    // then
    response.asserting(_ shouldBe expectedResponse)
  }
}
