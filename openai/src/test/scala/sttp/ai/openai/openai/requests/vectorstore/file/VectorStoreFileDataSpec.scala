package sttp.ai.openai.requests.vectorstore.file

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.fixtures.VectorStoreFileFixture
import sttp.ai.openai.requests.vectorstore.file.VectorStoreFileRequestBody.{CreateVectorStoreFileBody, ListVectorStoreFilesBody}
import sttp.ai.openai.requests.vectorstore.file.VectorStoreFileResponseData._
import io.circe.parser.{decode, parse}
import io.circe.syntax._
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.json.OpenAIManualCodecs._

class VectorStoreFileDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given create vector store file request" should "be properly serialized to Json" in {
    // given
    val givenRequest = CreateVectorStoreFileBody(
      fileId = "file_1"
    )

    val jsonRequest: io.circe.Json = parse(VectorStoreFileFixture.jsonCreateRequest).value

    // when
    val serializedJson: io.circe.Json = givenRequest.asJson.deepDropNullValues

    // then
    serializedJson shouldBe jsonRequest
  }

  "Vector store file search params" should "be properly serialized to Json" in {
    // given
    val givenRequest = ListVectorStoreFilesBody(
      limit = 30,
      order = "asc",
      after = Some("111"),
      before = Some("222"),
      filter = Some(InProgress)
    )

    val jsonRequest: io.circe.Json = parse(VectorStoreFileFixture.jsonListRequest).value

    // when
    val serializedJson: io.circe.Json = givenRequest.asJson.deepDropNullValues

    // then
    serializedJson shouldBe jsonRequest
  }

  "Vector store file response" should "be properly deserialized from Json" in {
    import sttp.ai.openai.requests.vectorstore.file.VectorStoreFileResponseData.VectorStoreFile._
    // given
    val givenResponse = VectorStoreFile(
      id = "vsf_1",
      `object` = "vector_store.file",
      createdAt = 1698107661,
      usageBytes = 123456,
      status = Completed,
      vectorStoreId = "vs_1"
    )
    val jsonResponse = VectorStoreFileFixture.jsonObject

    // when
    val serializedJson: Either[Exception, VectorStoreFile] = decode[VectorStoreFile](jsonResponse)

    // then
    serializedJson.value shouldBe givenResponse
  }

  "Vector store file response with error" should "be properly deserialized from Json" in {
    import sttp.ai.openai.requests.vectorstore.file.VectorStoreFileResponseData.VectorStoreFile._
    // given
    val givenResponse = VectorStoreFile(
      id = "vsf_1",
      `object` = "vector_store.file",
      createdAt = 1698107661,
      usageBytes = 123456,
      status = Completed,
      vectorStoreId = "vs_1",
      lastError = Some(LastError(ErrorCode.ServerError, "Failed"))
    )
    val jsonResponse = VectorStoreFileFixture.jsonObjectWithLastError

    // when
    val serializedJson: Either[Exception, VectorStoreFile] = decode[VectorStoreFile](jsonResponse)

    // then
    serializedJson.value shouldBe givenResponse
  }

  "Vector store file list response" should "be properly deserialized from Json" in {
    import sttp.ai.openai.requests.vectorstore.file.VectorStoreFileResponseData.ListVectorStoreFilesResponse._
    // given
    val one = VectorStoreFile(
      id = "vsf_1",
      `object` = "vector_store.file",
      createdAt = 1698107661,
      usageBytes = 123456,
      status = InProgress,
      vectorStoreId = "vs_1",
      lastError = None
    )

    val two = VectorStoreFile(
      id = "vsf_2",
      `object` = "vector_store.file",
      createdAt = 1698107661,
      usageBytes = 1234567,
      status = Completed,
      vectorStoreId = "vs_1",
      lastError = Some(LastError(ErrorCode.RateLimitExceeded, "Failed2"))
    )

    val givenResponse = ListVectorStoreFilesResponse(
      `object` = "list",
      data = Seq(one, two),
      firstId = "vsf_1",
      lastId = "vsf_2",
      hasMore = true
    )
    val jsonResponse = VectorStoreFileFixture.jsonList

    // when
    val serializedJson: Either[Exception, ListVectorStoreFilesResponse] =
      decode[ListVectorStoreFilesResponse](jsonResponse)

    // then
    serializedJson.value shouldBe givenResponse
  }

  "Delete of vector store file response" should "be properly deserialized from Json" in {
    import sttp.ai.openai.requests.vectorstore.file.VectorStoreFileResponseData.DeleteVectorStoreFileResponse._
    // given

    val givenResponse = DeleteVectorStoreFileResponse(
      id = "file_abc123",
      `object` = "vector_store.file.deleted",
      deleted = true
    )

    val jsonResponse = VectorStoreFileFixture.jsonDelete

    // when
    val serializedJson: Either[Exception, DeleteVectorStoreFileResponse] =
      decode[DeleteVectorStoreFileResponse](jsonResponse)

    // then
    serializedJson.value shouldBe givenResponse
  }
}
