package sttp.ai.openai.requests

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.fixtures.BatchFixture
import sttp.ai.openai.requests.batch.{BatchRequestBody, BatchResponse, ListBatchResponse}
import io.circe.parser.{decode, parse}
import io.circe.syntax._
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.json.OpenAIManualCodecs._

class BatchDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given create batch request as case class" should "be properly serialized to Json" in {
    // given
    val givenRequest = BatchRequestBody(
      inputFileId = "file-id",
      endpoint = "/v1/chat/completions",
      completionWindow = "24h",
      metadata = Some(Map("key1" -> "value1", "key2" -> "value2"))
    )
    val jsonRequest: io.circe.Json = parse(BatchFixture.jsonCreateBatchRequest).value
    // when
    val serializedJson: io.circe.Json = givenRequest.asJson.deepDropNullValues
    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create batch response as Json" should "be properly deserialized to case class" in {
    // given
    val jsonResponse = BatchFixture.jsonCreateBatchResponse
    val expectedResponse: BatchResponse = BatchFixture.batchResponse
    // when
    val deserializedJsonResponse: Either[Exception, BatchResponse] =
      decode[BatchResponse](jsonResponse)
    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

  "Given list batch response as Json" should "be properly deserialized to case class" in {
    // given
    val jsonResponse = BatchFixture.jsonListBatchResponse
    val expectedResponse: ListBatchResponse = ListBatchResponse(
      data = Seq(BatchFixture.batchResponse),
      hasMore = true,
      firstId = "ftckpt_zc4Q7MP6XxulcVzj4MZdwsAB",
      lastId = "ftckpt_enQCFmOTGj3syEpYVhBRLTSy"
    )
    // when
    val deserializedJsonResponse: Either[Exception, ListBatchResponse] =
      decode[ListBatchResponse](jsonResponse)

    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

}
