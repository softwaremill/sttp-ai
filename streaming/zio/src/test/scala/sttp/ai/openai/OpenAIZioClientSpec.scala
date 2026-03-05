package sttp.ai.openai

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.json.SnakePickle.read
import sttp.ai.openai.OpenAIExceptions.OpenAIException
import sttp.ai.openai.OpenAIExceptions.OpenAIException.DeserializationOpenAIException
import sttp.ai.openai.fixtures.{CompletionsFixture, ErrorFixture}
import sttp.ai.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse
import sttp.ai.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse.DoneEvent
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.utils.JsonUtils.compactJson
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.StatusCode
import sttp.model.sse.ServerSentEvent
import zio.stream.{ZPipeline, ZStream}
import zio.{Chunk, Runtime, Unsafe, ZIO, ZLayer}

class OpenAIZioClientSpec extends AnyFlatSpec with Matchers with EitherValues {
  private val runtime: Runtime[Any] = Runtime.default

  for ((statusCode, expectedError) <- ErrorFixture.testData)
    s"Service response with status code: $statusCode" should s"return properly deserialized ${expectedError.getClass.getSimpleName}" in {
      // given
      val zioBackendStub = HttpClientZioBackend.stub.whenAnyRequest.thenRespondAdjust(ErrorFixture.errorResponse, statusCode)
      val client = new OpenAI("test-token")

      val givenRequest = ChatBody(
        model = ChatCompletionModel.GPT35Turbo,
        messages = Seq.empty
      )

      // when
      val caughtEffect: ZIO[OpenAIZioClient, Nothing, OpenAIException] = for {
        client <- ZIO.service[OpenAIZioClient]
        res <- client.createChatCompletion(givenRequest).either
      } yield res.left.value

      val caught = unsafeRun(caughtEffect.provide(OpenAIZioClient.layer, ZLayer.succeed(zioBackendStub), ZLayer.succeed(client)))

      // then
      caught.getClass shouldBe expectedError.getClass
      caught.message shouldBe expectedError.message
      caught.cause.getClass shouldBe expectedError.cause.getClass
      caught.code shouldBe expectedError.code
      caught.param shouldBe expectedError.param
      caught.`type` shouldBe expectedError.`type`
    }

  case class Step(explanation: String, output: String)

  case class MathReasoning(steps: List[Step], finalAnswer: String)

  "typed createChatCompletion" should "be ok" in {
    // given
    val zioBackendStub = HttpClientZioBackend.stub.whenAnyRequest.thenRespondAdjust(CompletionsFixture.structuredOutputsResponse, StatusCode.Ok)
    val client = new OpenAI("test-token")

    // when
    val mockRes = MathReasoning(Nil, "final answer")
    import sttp.tapir.generic.auto._

    val effect: ZIO[OpenAIZioClient, OpenAIException, MathReasoning] = for {
      client <- ZIO.service[OpenAIZioClient]
      res <- client.createChatCompletion[MathReasoning](ChatBody(Nil, ChatCompletionModel.GPT4oMini)) { _ =>
        Right(mockRes)
      }
    } yield res

    val res = unsafeRun(effect.provide(OpenAIZioClient.layer, ZLayer.succeed(zioBackendStub), ZLayer.succeed(client)))

    // then
    res shouldBe mockRes
  }

  "typed createChatCompletion" should "throw exception with parsed error" in {
    // given
    val zioBackendStub = HttpClientZioBackend.stub.whenAnyRequest.thenRespondAdjust(CompletionsFixture.structuredOutputsResponse, StatusCode.Ok)
    val client = new OpenAI("test-token")

    // when
    import sttp.tapir.generic.auto._

    val caughtEffect = for {
      client <- ZIO.service[OpenAIZioClient]
      res <- client.createChatCompletion[MathReasoning](ChatBody(Nil, ChatCompletionModel.GPT4oMini)) { _ =>
        Left("parsed error")
      }.either
    } yield res.left.value

    val caught = unsafeRun(caughtEffect.provide(OpenAIZioClient.layer, ZLayer.succeed(zioBackendStub), ZLayer.succeed(client)))

    val expectedError = new DeserializationOpenAIException("parsed error", null)
    caught.getClass shouldBe expectedError.getClass: Unit
    caught.message shouldBe expectedError.message: Unit
    caught.cause shouldBe null
    caught.code shouldBe expectedError.code: Unit
    caught.param shouldBe expectedError.param: Unit
    caught.`type` shouldBe expectedError.`type`
  }

  "typed createChatCompletion" should "throw exception without choices" in {
    // given
    val zioBackendStub = HttpClientZioBackend.stub.whenAnyRequest.thenRespondAdjust(CompletionsFixture.structuredOutputsResponseWithoutChoices, StatusCode.Ok)
    val client = new OpenAI("test-token")

    // when
    import sttp.tapir.generic.auto._
    val mockRes = MathReasoning(Nil, "final answer")
    val caughtEffect = for {
      client <- ZIO.service[OpenAIZioClient]
      res <- client.createChatCompletion[MathReasoning](ChatBody(Nil, ChatCompletionModel.GPT4oMini)) { _ =>
        Right(mockRes)
      }.either
    } yield res.left.value

    val caught = unsafeRun(caughtEffect.provide(OpenAIZioClient.layer, ZLayer.succeed(zioBackendStub), ZLayer.succeed(client)))

    // then
    val expectedError = new DeserializationOpenAIException("no choices found in response", null)
    caught.getClass shouldBe expectedError.getClass: Unit
    caught.message shouldBe expectedError.message: Unit
    caught.cause shouldBe null
    caught.code shouldBe expectedError.code: Unit
    caught.param shouldBe expectedError.param: Unit
    caught.`type` shouldBe expectedError.`type`
  }

  "Creating chat completions with successful response" should "ignore empty events and return properly deserialized list of chunks" in {
    // given
    val chatChunks = Seq.fill(3)(sttp.ai.openai.fixtures.ChatChunkFixture.jsonResponse).map(compactJson)

    val eventsToProcess = chatChunks.map(data => ServerSentEvent(Some(data)))
    val emptyEvent = ServerSentEvent()
    val events = (eventsToProcess :+ emptyEvent) :+ DoneEvent

    val delimiter = "\n\n"
    val streamedResponse = ZStream
      .from(events)
      .map(_.toString + delimiter)
      .via(ZPipeline.utf8Encode)

    val zioBackendStub = HttpClientZioBackend.stub.whenAnyRequest.thenRespondAdjust(streamedResponse)
    val client = new OpenAI("test-token")

    val givenRequest = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = Seq.empty
    )

    // when
    val streamEffect = for {
      client <- ZIO.service[OpenAIZioClient]
      res <- client.createStreamedChatCompletion(givenRequest)
      finalRes <- res.runCollect
    } yield finalRes

    val response: Chunk[ChatChunkResponse] = unsafeRun(streamEffect.provide(OpenAIZioClient.layer, ZLayer.succeed(zioBackendStub), ZLayer.succeed(client)))

    // then
    response.toList shouldBe chatChunks.map(read[ChatChunkResponse](_))
  }

  private def unsafeRun[E, A](zio: ZIO[Any, E, A]): A =
    Unsafe.unsafe(implicit unsafe => runtime.unsafe.run(zio).getOrThrowFiberFailure())
}
