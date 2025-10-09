package sttp.openai.streaming.pekko

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.util.ByteString
import org.scalatest.EitherValues
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.pekkohttp.PekkoHttpBackend
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

class PekkoClientSpec extends AsyncFlatSpec with Matchers with EitherValues {
  implicit val system: ActorSystem = ActorSystem()

  "Creating speech" should "return byte stream" in {
    // given
    val expectedResponse = "audio content"
    val pekkoBackendStub = PekkoHttpBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(Source(ByteString(expectedResponse))))
    val client = new OpenAI(authToken = "test-token")
    val givenRequest = SpeechRequestBody(
      model = TTS1,
      input = "Hello, my name is John.",
      voice = Voice.Alloy
    )
    // when
    val response = client
      .createSpeech(givenRequest)
      .send(pekkoBackendStub)
      .map(_.body.value)
      .flatMap(_.runWith(Sink.seq))
    // then
    response.map(_ shouldBe expectedResponse.getBytes.toSeq)
  }

  for ((statusCode, expectedError) <- ErrorFixture.testData)
    s"Service response with status code: $statusCode" should s"return properly deserialized ${expectedError.getClass.getSimpleName}" in {
      // given
      val pekkoBackendStub = PekkoHttpBackend.stub.whenAnyRequest.thenRespondAdjust(ErrorFixture.errorResponse, statusCode)
      val client = new OpenAI("test-token")

      val givenRequest = ChatBody(
        model = ChatCompletionModel.GPT35Turbo,
        messages = Seq.empty
      )

      // when
      val caught = client
        .createStreamedChatCompletion(givenRequest)
        .send(pekkoBackendStub)
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

    val pekkoBackendStub = PekkoHttpBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(streamedResponse))
    val client = new OpenAI(authToken = "test-token")

    val givenRequest = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = Seq.empty
    )

    // when
    val response = client
      .createStreamedChatCompletion(givenRequest)
      .send(pekkoBackendStub)
      .map(_.body.value)
      .flatMap(_.run())

    // then
    response.failed.map(_ shouldBe a[DeserializationOpenAIException])
  }

  "Creating chat completions with successful response" should "ignore empty events and return properly deserialized list of chunks" in {
    // given
    val chatChunks = Seq.fill(3)(sttp.openai.fixtures.ChatChunkFixture.jsonResponse).map(compactJson)

    val eventsToProcess = chatChunks.map(data => ServerSentEvent(Some(data)))
    val emptyEvent = ServerSentEvent()
    val events = (eventsToProcess :+ emptyEvent) :+ DoneEvent

    val delimiter = "\n\n"
    val streamedResponse = Source(events)
      .map(_.toString + delimiter)
      .map(ByteString(_))

    // when & then
    assertStreamedCompletion(streamedResponse, chatChunks.map(read[ChatChunkResponse](_)))
  }

  "Creating chat completions with successful response" should "stop listening after [DONE] event and return properly deserialized list of chunks" in {
    // given
    val chatChunks = Seq.fill(3)(sttp.openai.fixtures.ChatChunkFixture.jsonResponse).map(compactJson)

    val eventsToProcess = chatChunks.map(data => ServerSentEvent(Some(data)))
    val events = (eventsToProcess :+ DoneEvent) ++ eventsToProcess

    val delimiter = "\n\n"
    val streamedResponse = Source(events)
      .map(_.toString + delimiter)
      .map(ByteString(_))

    // when & then
    assertStreamedCompletion(streamedResponse, chatChunks.map(read[ChatChunkResponse](_)))
  }

  private def assertStreamedCompletion(givenResponse: Source[ByteString, NotUsed], expectedResponse: Seq[ChatChunkResponse]) = {
    val pekkoBackendStub = PekkoHttpBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(givenResponse))
    val client = new OpenAI(authToken = "test-token")

    val givenRequest = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = Seq.empty
    )

    // when
    val response = client
      .createStreamedChatCompletion(givenRequest)
      .send(pekkoBackendStub)
      .map(_.body.value)
      .flatMap(_.runWith(Sink.seq))

    // then
    response.map(_ shouldBe expectedResponse)
  }
}
