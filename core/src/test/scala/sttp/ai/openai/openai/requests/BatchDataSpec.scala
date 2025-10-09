package sttp.openai.requests

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures.BatchFixture
import sttp.openai.json.SnakePickle
import sttp.openai.requests.batch.{BatchRequestBody, BatchResponse, ListBatchResponse}
import sttp.openai.utils.JsonUtils

class BatchDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given create batch request as case class" should "be properly serialized to Json" in {
    // given
    val givenRequest = BatchRequestBody(
      inputFileId = "file-id",
      endpoint = "/v1/chat/completions",
      completionWindow = "24h",
      metadata = Some(Map("key1" -> "value1", "key2" -> "value2"))
    )
    val jsonRequest: ujson.Value = ujson.read(BatchFixture.jsonCreateBatchRequest)
    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)
    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create batch response as Json" should "be properly deserialized to case class" in {
    // given
    val jsonResponse = BatchFixture.jsonCreateBatchResponse
    val expectedResponse: BatchResponse = BatchFixture.batchResponse
    // when
    val deserializedJsonResponse: Either[Exception, BatchResponse] =
      JsonUtils.deserializeJsonSnake[BatchResponse].apply(jsonResponse)
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
      JsonUtils.deserializeJsonSnake[ListBatchResponse].apply(jsonResponse)

    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

}
