package sttp.ai.openai.client

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.ResponseException.UnexpectedStatusCode
import sttp.client4._
import sttp.client4.testing.ResponseStub
import sttp.model.StatusCode
import sttp.model.StatusCode._
import sttp.ai.openai.OpenAIExceptions.OpenAIException
import sttp.ai.openai.OpenAIExceptions.OpenAIException.DeserializationOpenAIException
import sttp.ai.openai.fixtures.ErrorFixture
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.models.ModelsResponseData._
import sttp.ai.openai.requests.responses.ResponsesModel.GPT4oMini
import sttp.ai.core.json.SnakePickle
import sttp.ai.openai.{CustomizeOpenAIRequest, OpenAISyncClient}
import sttp.tapir.Schema

import java.util.concurrent.atomic.AtomicReference

class SyncClientSpec extends AnyFlatSpec with Matchers with EitherValues {
  for ((statusCode, expectedError) <- ErrorFixture.testData)
    s"Service response with status code: $statusCode" should s"return properly deserialized ${expectedError.getClass.getSimpleName}" in {
      // given
      val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(ErrorFixture.errorResponse, statusCode)
      val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

      // when
      val caught = intercept[OpenAIException](syncClient.getModels)

      // then
      caught.getClass shouldBe expectedError.getClass: Unit
      caught.message shouldBe expectedError.message: Unit
      caught.cause.getClass shouldBe expectedError.cause.getClass: Unit
      caught.code shouldBe expectedError.code: Unit
      caught.param shouldBe expectedError.param: Unit
      caught.`type` shouldBe expectedError.`type`
    }

  "Service response with an Azure-style error body" should "fall back to the raw body and status code" in {
    // given
    val azureBody = """{"statusCode":404,"message":"Resource not found"}"""
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(azureBody, NotFound)
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

    // when
    val caught = intercept[OpenAIException](syncClient.getModels)

    // then
    caught.getClass shouldBe classOf[OpenAIException.InvalidRequestException]: Unit
    caught.message shouldBe Some(azureBody): Unit
    caught.code shouldBe Some(NotFound.code.toString): Unit
    caught.param shouldBe None: Unit
    caught.`type` shouldBe None: Unit
    caught.cause.getClass shouldBe classOf[UnexpectedStatusCode[String]]
  }

  "Service response with a non-object JSON root" should "fall back to the raw body and status code" in {
    // given
    val arrayBody = """["unexpected","array"]"""
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(arrayBody, BadRequest)
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

    // when
    val caught = intercept[OpenAIException](syncClient.getModels)

    // then
    caught.getClass shouldBe classOf[OpenAIException.InvalidRequestException]: Unit
    caught.message shouldBe Some(arrayBody): Unit
    caught.code shouldBe Some(BadRequest.code.toString): Unit
    caught.param shouldBe None: Unit
    caught.`type` shouldBe None
  }

  "Service response with a malformed non-JSON body" should "fall back to the raw body and status code" in {
    // given
    val malformedBody = "not even json <html>500</html>"
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(malformedBody, ServiceUnavailable)
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

    // when
    val caught = intercept[OpenAIException](syncClient.getModels)

    // then
    caught.getClass shouldBe classOf[OpenAIException.ServiceUnavailableException]: Unit
    caught.message shouldBe Some(malformedBody): Unit
    caught.code shouldBe Some(ServiceUnavailable.code.toString)
  }

  "Service response with an error body longer than the max" should "truncate the fallback message" in {
    // given
    val longBody = "x" * 600
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(longBody, BadRequest)
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

    // when
    val caught = intercept[OpenAIException](syncClient.getModels)

    // then
    caught.message.map(_.length) shouldBe Some(500): Unit
    caught.message shouldBe Some(longBody.take(500))
  }

  "Service response with a present but malformed error node" should "fall back to the raw body and status code" in {
    // given
    val malformedErrorBody = """{"error":"unstructured string"}"""
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(malformedErrorBody, BadRequest)
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

    // when
    val caught = intercept[OpenAIException](syncClient.getModels)

    // then
    caught.getClass shouldBe classOf[OpenAIException.InvalidRequestException]: Unit
    caught.message shouldBe Some(malformedErrorBody): Unit
    caught.code shouldBe Some(BadRequest.code.toString): Unit
    caught.param shouldBe None: Unit
    caught.`type` shouldBe None
  }

  "Service response with the standard OpenAI error body" should "still map to the right subclass with parsed fields" in {
    // given
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(ErrorFixture.errorResponse, Unauthorized)
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

    // when
    val caught = intercept[OpenAIException](syncClient.getModels)

    // then
    caught.getClass shouldBe classOf[OpenAIException.AuthenticationException]: Unit
    caught.message shouldBe Some("Some error message."): Unit
    caught.`type` shouldBe Some("error_type"): Unit
    caught.param shouldBe None: Unit
    caught.code shouldBe Some("invalid_api_key")
  }

  "Fetching models with successful response" should "return properly deserialized list of available models" in {
    // given
    val modelsResponse = sttp.ai.openai.fixtures.ModelsGetResponse.singleModelResponse
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(modelsResponse, Ok)
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)
    val deserializedModels = ModelsResponse(
      `object` = "list",
      data = Seq(
        ModelData(
          id = "babbage",
          `object` = "model",
          created = 1649358449,
          ownedBy = "openai"
        )
      )
    )

    // when & then
    syncClient.getModels shouldBe deserializedModels
  }

  "Customizing the request" should "be additive" in {
    // given
    val capturedRequest = new AtomicReference[GenericRequest[_, _]](null)
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { request =>
      capturedRequest.set(request)
      ResponseStub.adjust(sttp.ai.openai.fixtures.ModelsGetResponse.singleModelResponse, StatusCode.Ok)
    }
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

    // when
    syncClient
      .customizeRequest(new CustomizeOpenAIRequest {
        override def apply[A](request: Request[Either[OpenAIException, A]]): Request[Either[OpenAIException, A]] =
          request.header("X-Test", "test")
      })
      .customizeRequest(new CustomizeOpenAIRequest {
        override def apply[A](request: Request[Either[OpenAIException, A]]): Request[Either[OpenAIException, A]] =
          request.header("X-Test-2", "test-2")
      })
      .getModels: Unit

    // then
    capturedRequest.get().headers.find(_.is("X-Test")).map(_.value) shouldBe Some("test"): Unit
    capturedRequest.get().headers.find(_.is("X-Test-2")).map(_.value) shouldBe Some("test-2")
  }

  case class Step(explanation: String, output: String)
  object Step {
    implicit val rw: SnakePickle.ReadWriter[Step] = SnakePickle.macroRW
  }

  case class MathReasoning(steps: List[Step], finalAnswer: String)
  object MathReasoning {
    implicit val rw: SnakePickle.ReadWriter[MathReasoning] = SnakePickle.macroRW
  }

  "typed createChatCompletion" should "be ok" in {
    // given
    val capturedRequest = new AtomicReference[GenericRequest[_, _]](null)
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { request =>
      capturedRequest.set(request)
      ResponseStub.adjust(sttp.ai.openai.fixtures.CompletionsFixture.structuredOutputsResponse, StatusCode.Ok)
    }
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

    // when
    val mockRes = MathReasoning(Nil, "final answer")
    import sttp.tapir.generic.auto._
    val res = syncClient.createChatCompletion[MathReasoning](ChatBody(Nil, ChatCompletionModel.GPT4oMini)) { body =>
      Right(mockRes)
    }

    // then
    res shouldBe mockRes
  }

  "typed createChatCompletion" should "throw exception with parsed error" in {
    // given
    val capturedRequest = new AtomicReference[GenericRequest[_, _]](null)
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { request =>
      capturedRequest.set(request)
      ResponseStub.adjust(sttp.ai.openai.fixtures.CompletionsFixture.structuredOutputsResponse, StatusCode.Ok)
    }
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

    // when
    import sttp.tapir.generic.auto._
    val caught =
      intercept[OpenAIException](syncClient.createChatCompletion[MathReasoning](ChatBody(Nil, ChatCompletionModel.GPT4oMini)) { body =>
        Left("parsed error")
      })

    val expectedError = new DeserializationOpenAIException("parsed error", null)
    caught.getClass shouldBe expectedError.getClass: Unit
    caught.message shouldBe expectedError.message: Unit
    caught.cause shouldBe null
    caught.code shouldBe expectedError.code: Unit
    caught.param shouldBe expectedError.param: Unit
    caught.`type` shouldBe expectedError.`type`
  }

  "createChatCompletionAs" should "parse the response into the typed value via uPickle" in {
    // given
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(
      sttp.ai.openai.fixtures.CompletionsFixture.structuredOutputsResponse,
      StatusCode.Ok
    )
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

    // when
    import sttp.tapir.generic.auto._
    val res: MathReasoning = syncClient.createChatCompletionAs[MathReasoning](ChatBody(Nil, ChatCompletionModel.GPT4oMini))

    // then
    res.finalAnswer should include("x ="): Unit
    res.steps should have size 6
    res.steps.head.output shouldBe "8x + 7 = -23"
  }

  "createChatCompletionAs" should "throw a DeserializationOpenAIException when the content is not valid JSON for T" in {
    // given
    val unparseableContent =
      """{"id":"x","object":"chat.completion","created":0,"model":"m","choices":[{"index":0,"message":{"role":"assistant","content":"not-json"},"finish_reason":"stop"}],"usage":{"prompt_tokens":0,"completion_tokens":0,"total_tokens":0}}"""
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(unparseableContent, StatusCode.Ok)
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

    // when
    import sttp.tapir.generic.auto._
    intercept[DeserializationOpenAIException](
      syncClient.createChatCompletionAs[MathReasoning](ChatBody(Nil, ChatCompletionModel.GPT4oMini))
    ): Unit
  }

  "typed createChatCompletion" should "throw exception without choices" in {
    // given
    val capturedRequest = new AtomicReference[GenericRequest[_, _]](null)
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { request =>
      capturedRequest.set(request)
      ResponseStub.adjust(sttp.ai.openai.fixtures.CompletionsFixture.structuredOutputsResponseWithoutChoices, StatusCode.Ok)
    }
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

    // when
    val mockRes = MathReasoning(Nil, "final answer")
    import sttp.tapir.generic.auto._
    val caught =
      intercept[OpenAIException](syncClient.createChatCompletion[MathReasoning](ChatBody(Nil, ChatCompletionModel.GPT4oMini)) { body =>
        Right(mockRes)
      })

    // then
    val expectedError = new DeserializationOpenAIException("no choices found in response", null)
    caught.getClass shouldBe expectedError.getClass: Unit
    caught.message shouldBe expectedError.message: Unit
    caught.cause shouldBe null
    caught.code shouldBe expectedError.code: Unit
    caught.param shouldBe expectedError.param: Unit
    caught.`type` shouldBe expectedError.`type`
  }
}
