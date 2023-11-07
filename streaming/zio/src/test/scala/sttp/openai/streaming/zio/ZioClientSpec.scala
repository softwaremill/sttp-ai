package sttp.openai.streaming.zio

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.DeserializationException
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.client4.testing.RawStream
import sttp.model.sse.ServerSentEvent
import sttp.openai.fixtures.ErrorFixture
import sttp.openai.json.SnakePickle._
import sttp.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse
import sttp.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse.DoneEventMessage
import sttp.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.openai.utils.JsonUtils.compactJson
import sttp.openai.{OpenAI, OpenAIExceptions}
import zio._
import zio.stream._

class ZioClientSpec extends AnyFlatSpec with Matchers with EitherValues {
  private val runtime: Runtime[Any] = Runtime.default

  for ((statusCode, expectedError) <- ErrorFixture.testData)
    s"Service response with status code: $statusCode" should s"return properly deserialized ${expectedError.getClass.getSimpleName}" in {
      // given
      val zioBackendStub = HttpClientZioBackend.stub.whenAnyRequest.thenRespondWithCode(statusCode, ErrorFixture.errorResponse)
      val client = new OpenAI("test-token")

      val givenRequest = ChatBody(
        model = ChatCompletionModel.GPT35Turbo,
        messages = Seq.empty
      )

      // when
      val caughtEffect: ZIO[Any, Throwable, OpenAIExceptions.OpenAIException] = client
        .createStreamedChatCompletion(givenRequest)
        .send(zioBackendStub)
        .map(_.body.left.value)

      val caught = unsafeRun(caughtEffect)

      // then
      caught.getClass shouldBe expectedError.getClass
      caught.message shouldBe expectedError.message
      caught.cause shouldBe expectedError.cause
      caught.code shouldBe expectedError.code
      caught.param shouldBe expectedError.param
      caught.`type` shouldBe expectedError.`type`
    }

  "Creating chat completions with failed stream due to invalid deserialization" should "return properly deserialized error" in {
    // given
    val invalidJson = Some("invalid json")
    val invalidEvent = ServerSentEvent(invalidJson)

    val streamedResponse = ZStream
      .succeed(invalidEvent.toString)
      .via(ZPipeline.utf8Encode)

    val zioBackendStub = HttpClientZioBackend.stub.whenAnyRequest.thenRespond(RawStream(streamedResponse))
    val client = new OpenAI(authToken = "test-token")

    val givenRequest = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = Seq.empty
    )

    // when
    val responseEffect = client
      .createStreamedChatCompletion(givenRequest)
      .send(zioBackendStub)
      .flatMap(_.body.value.runDrain)

    val response = unsafeRun(responseEffect.either)

    // then
    response shouldBe a[Left[DeserializationException[_], _]]
  }

  "Creating chat completions with successful response" should "return properly deserialized list of chunks" in {
    // given
    val chatChunks = Seq.fill(3)(sttp.openai.fixtures.ChatChunkFixture.jsonResponse).map(compactJson)
    val events = chatChunks.map(data => ServerSentEvent(Some(data))) :+ ServerSentEvent(Some(DoneEventMessage))
    val delimiter = "\n\n"
    val streamedResponse = ZStream
      .from(events)
      .map(_.toString + delimiter)
      .via(ZPipeline.utf8Encode)

    val zioBackendStub = HttpClientZioBackend.stub.whenAnyRequest.thenRespond(RawStream(streamedResponse))
    val client = new OpenAI(authToken = "test-token")

    val givenRequest = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = Seq.empty
    )

    // when
    val responseEffect = client
      .createStreamedChatCompletion(givenRequest)
      .send(zioBackendStub)
      .map(_.body.value)
      .flatMap(_.runCollect)

    val response = unsafeRun(responseEffect)

    // then
    val expectedResponse = chatChunks.map(read[ChatChunkResponse](_))
    response.toList shouldBe expectedResponse
  }

  private def unsafeRun[E, A](zio: ZIO[Any, E, A]): A =
    Unsafe.unsafe(implicit unsafe => runtime.unsafe.run(zio).getOrThrowFiberFailure())
}
