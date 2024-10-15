package sttp.openai.client

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.model.StatusCode._
import sttp.openai.OpenAIExceptions.OpenAIException
import sttp.openai.OpenAISyncClient
import sttp.openai.fixtures.ErrorFixture
import sttp.openai.requests.models.ModelsResponseData._
import sttp.openai.CustomizeOpenAIRequest
import java.util.concurrent.atomic.AtomicReference
import sttp.client4.testing.ResponseStub

class SyncClientSpec extends AnyFlatSpec with Matchers with EitherValues {
  for ((statusCode, expectedError) <- ErrorFixture.testData)
    s"Service response with status code: $statusCode" should s"return properly deserialized ${expectedError.getClass.getSimpleName}" in {
      // given
      val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondWithCode(statusCode, ErrorFixture.errorResponse)
      val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)

      // when
      val caught = intercept[OpenAIException](syncClient.getModels)

      // then
      caught.getClass shouldBe expectedError.getClass: Unit
      caught.message shouldBe expectedError.message: Unit
      caught.cause shouldBe expectedError.cause: Unit
      caught.code shouldBe expectedError.code: Unit
      caught.param shouldBe expectedError.param: Unit
      caught.`type` shouldBe expectedError.`type`
    }

  "Fetching models with successful response" should "return properly deserialized list of available models" in {
    // given
    val modelsResponse = sttp.openai.fixtures.ModelsGetResponse.singleModelResponse
    val syncBackendStub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondWithCode(Ok, modelsResponse)
    val syncClient = OpenAISyncClient(authToken = "test-token", backend = syncBackendStub)
    val deserializedModels = ModelsResponse(
      `object` = "list",
      data = Seq(
        ModelData(
          id = "babbage",
          `object` = "model",
          created = 1649358449,
          ownedBy = "openai",
          permission = Seq(
            ModelPermission(
              id = "modelperm-49FUp5v084tBB49tC4z8LPH5",
              `object` = "model_permission",
              created = 1669085501,
              allowCreateEngine = false,
              allowSampling = true,
              allowLogprobs = true,
              allowSearchIndices = false,
              allowView = true,
              allowFineTuning = false,
              organization = "*",
              group = None,
              isBlocking = false
            )
          ),
          root = "babbage",
          parent = None
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
      ResponseStub.ok(sttp.openai.fixtures.ModelsGetResponse.singleModelResponse)
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
}
