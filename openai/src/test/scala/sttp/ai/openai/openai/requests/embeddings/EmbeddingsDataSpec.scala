package sttp.ai.openai.requests.embeddings

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.fixtures
import sttp.ai.openai.requests.embeddings.EmbeddingsRequestBody.EmbeddingsModel
import sttp.ai.openai.requests.embeddings.EmbeddingsResponseBody._
import io.circe.parser.decode
import io.circe.Json
import io.circe.syntax._
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.json.OpenAIManualCodecs._

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
      decode[EmbeddingResponse](listFilesResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given embeddings request with extraBody" should "merge extraBody entries into the top level of the serialized Json" in {
    val givenRequest = EmbeddingsRequestBody.EmbeddingsBody(
      model = EmbeddingsModel.TextEmbeddingAda002,
      input = EmbeddingsRequestBody.EmbeddingsInput.SingleInput("hello"),
      extraBody = Map("truncate" -> Json.fromString("END"))
    )

    val serializedJson = givenRequest.asJson.deepDropNullValues

    serializedJson.hcursor.downField("truncate").as[String].value shouldBe "END"
    serializedJson.hcursor.downField("extra_body").succeeded shouldBe false
  }

}
