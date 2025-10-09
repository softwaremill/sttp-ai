package sttp.openai.streaming.ox

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.{supervised, Ox}
import sttp.client4.DefaultSyncBackend
import sttp.client4.testing.ResponseStub
import sttp.model.sse.ServerSentEvent
import sttp.openai.OpenAI
import sttp.openai.OpenAIExceptions.OpenAIException.DeserializationOpenAIException
import sttp.openai.fixtures.ErrorFixture
import sttp.openai.json.SnakePickle.*
import sttp.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse
import sttp.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse.DoneEvent
import sttp.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.openai.utils.JsonUtils.compactJson

import java.io.{ByteArrayInputStream, InputStream}

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
    val chatChunks = Seq.fill(3)(sttp.openai.fixtures.ChatChunkFixture.jsonResponse).map(compactJson)

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
      assertStreamedCompletion(streamedResponse, chatChunks.map(read[ChatChunkResponse](_)))
    }
  }

  "Creating chat completions with successful response" should "stop listening after [DONE] event and return properly deserialized list of chunks" in {
    // given
    val chatChunks = Seq.fill(3)(sttp.openai.fixtures.ChatChunkFixture.jsonResponse).map(compactJson)

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
      assertStreamedCompletion(streamedResponse, chatChunks.map(read[ChatChunkResponse](_)))
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
}
