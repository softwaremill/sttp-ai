package sttp.openai.requests.files

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures
import sttp.openai.requests.files.FilesResponseData.FilesResponse._
import sttp.openai.requests.files.FilesResponseData.{DeletedFileData, FileData, FilesResponse}
import sttp.openai.utils.JsonUtils

class FilesResponseDataSpec extends AnyFlatSpec with Matchers with EitherValues {
  "Given list files response as Json" should "be properly deserialized to case class" in {
    // given
    val listFilesResponse = fixtures.FilesResponse.listFilesJsonResponse
    val expectedResponse = FilesResponse(
      `object` = "list",
      Seq(
        FileData(
          `object` = "file",
          id = "file-tralala",
          purpose = "fine-tune",
          filename = "example.jsonl",
          bytes = 44,
          createdAt = 1681375533,
          status = "processed",
          statusDetails = None
        )
      )
    )

    // when
    val givenResponse: Either[Exception, FilesResponse] = JsonUtils.deserializeJsonSnake.apply(listFilesResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given upload file response as Json" should "be properly deserialized to case class" in {
    // given
    val singleFileJsonResponse = fixtures.FilesResponse.singleFileJsonResponse
    val expectedResponse =
      FileData(
        `object` = "file",
        id = "file-tralala",
        purpose = "fine-tune",
        filename = "example.jsonl",
        bytes = 44,
        createdAt = 1681375533,
        status = "uploaded",
        statusDetails = None
      )

    // when
    val givenResponse: Either[Exception, FileData] = JsonUtils.deserializeJsonSnake[FileData].apply(singleFileJsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given delete file response as Json" should "be properly deserialized to case class" in {
    // given
    val listFilesResponse = fixtures.FilesResponse.deleteFileJsonResponse
    val expectedResponse = DeletedFileData(
      `object` = "file",
      id = "file-tralala",
      deleted = true
    )

    // when
    val givenResponse: Either[Exception, DeletedFileData] =
      JsonUtils.deserializeJsonSnake[DeletedFileData].apply(listFilesResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given retrieve file response as Json" should "be properly deserialized to case class" in {
    // given
    val retrieveFileJsonResponse = fixtures.FilesResponse.retrieveFileJsonResponse
    val expectedResponse = FileData(
      `object` = "file",
      id = "file-tralala",
      purpose = "fine-tune",
      filename = "example.jsonl",
      bytes = 44,
      createdAt = 1681375533,
      status = "processed",
      statusDetails = None
    )

    // when
    val givenResponse: Either[Exception, FileData] = JsonUtils.deserializeJsonSnake[FileData].apply(retrieveFileJsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }
}
