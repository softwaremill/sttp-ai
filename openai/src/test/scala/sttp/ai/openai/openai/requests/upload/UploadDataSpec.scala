package sttp.ai.openai.requests.upload

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.fixtures.UploadFixture
import io.circe.parser.{decode, parse}
import io.circe.syntax._
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.json.OpenAIManualCodecs._

class UploadDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given upload request body as case class" should "be properly serialized to Json" in {
    // given
    val givenRequest = UploadRequestBody(
      filename = "file-name",
      purpose = "file-purpose",
      bytes = 123,
      mimeType = "file/mime-type"
    )
    val jsonRequest: io.circe.Json = parse(UploadFixture.jsonCreateUpload).value
    // when
    val serializedJson: io.circe.Json = givenRequest.asJson.deepDropNullValues
    // then
    serializedJson shouldBe jsonRequest
  }

  "Given complete upload request body as case class" should "be properly serialized to Json" in {
    // given
    val givenRequest = CompleteUploadRequestBody(
      partIds = Seq("part_abc123", "part_def456"),
      md5 = Some("md5-checksum")
    )
    val jsonRequest: io.circe.Json = parse(UploadFixture.jsonCompleteUpload).value
    // when
    val serializedJson: io.circe.Json = givenRequest.asJson.deepDropNullValues
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
      file = Some(
        FileMetadata(
          id = "file-xyz321",
          bytes = 1147483648,
          createdAt = 1719186911,
          filename = "training_examples.jsonl",
          purpose = "fine-tune",
          `object` = "file"
        )
      )
    )
    // when
    val deserializedJsonResponse: Either[Exception, UploadResponse] =
      decode[UploadResponse](jsonResponse)
    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

  "Given upload part response as Json" should "be properly deserialized to case class" in {
    // given
    val jsonResponse = UploadFixture.jsonUploadPartResponse
    val expectedResponse: UploadPartResponse = UploadPartResponse(
      id = "part_def456",
      createdAt = 1719186911,
      uploadId = "upload_abc123"
    )
    // when
    val deserializedJsonResponse: Either[Exception, UploadPartResponse] =
      decode[UploadPartResponse](jsonResponse)
    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

}
