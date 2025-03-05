package sttp.openai.requests.upload

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures.UploadFixture
import sttp.openai.json.{SnakePickle, SttpUpickleApiExtension}

class UploadDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given upload request body as case class" should "be properly serialized to Json" in {
    // given
    val givenRequest = UploadRequestBody(
      filename = "file-name",
      purpose = "file-purpose",
      bytes = 123,
      mimeType = "file/mime-type"
    )
    val jsonRequest: ujson.Value = ujson.read(UploadFixture.jsonCreateUpload)
    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)
    // then
    serializedJson shouldBe jsonRequest
  }

  "Given upload response as Json" should "be properly deserialized to case class" in {
    // given
    val jsonResponse = UploadFixture.jsonUpdateResponse
    val expectedResponse: UploadResponse = UploadResponse(
      id = "upload_abc123",
      bytes = 1147483648,
      createdAt = 1719184911,
      filename = "training_examples.jsonl",
      purpose = "fine-tune",
      status = "completed",
      expiresAt = 1719127296,
      file = File(
        id = "file-xyz321",
        bytes = 1147483648,
        createdAt = 1719186911,
        filename = "training_examples.jsonl",
        purpose = "fine-tune",
        `object` = "file"
      )
    )
    // when
    val deserializedJsonResponse: Either[Exception, UploadResponse] =
      SttpUpickleApiExtension.deserializeJsonSnake[UploadResponse].apply(jsonResponse)
    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

}
