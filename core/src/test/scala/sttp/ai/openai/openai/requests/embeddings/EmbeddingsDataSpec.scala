package sttp.openai.requests.embeddings

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures
import sttp.openai.requests.embeddings.EmbeddingsRequestBody.EmbeddingsModel
import sttp.openai.requests.embeddings.EmbeddingsResponseBody._
import sttp.openai.utils.JsonUtils

class EmbeddingsDataSpec extends AnyFlatSpec with Matchers with EitherValues {
  "Given list files response as Json" should "be properly deserialized to case class" in {
    // given
    val listFilesResponse = fixtures.EmbeddingsFixture.jsonCreateEmbeddingsResponse
    val expectedResponse = EmbeddingResponse(
      `object` = "list",
      data = Seq(
        EmbeddingData(
          `object` = "embedding",
          index = 0,
          embedding = Seq(
            0.0023064255, -0.009327292, 0.015797347, -0.0077780345, -0.0046922187
          )
        )
      ),
      model = EmbeddingsModel.TextEmbeddingAda002,
      usage = Usage(
        promptTokens = 8,
        totalTokens = 8
      )
    )
    // when
    val givenResponse: Either[Exception, EmbeddingResponse] =
      JsonUtils.deserializeJsonSnake[EmbeddingResponse].apply(listFilesResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

}
