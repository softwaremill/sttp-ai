package sttp.openai.requests.vectorstore.file

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures.VectorStoreFileFixture
import sttp.openai.json.SnakePickle
import sttp.openai.requests.vectorstore.file.VectorStoreFileRequestBody.{CreateVectorStoreFileBody, ListVectorStoreFilesBody}
import sttp.openai.requests.vectorstore.file.VectorStoreFileResponseData._
import sttp.openai.utils.JsonUtils

class VectorStoreFileDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given create vector store file request" should "be properly serialized to Json" in {
    // given
    val givenRequest = CreateVectorStoreFileBody(
      fileId = "file_1"
    )

    val jsonRequest: ujson.Value = ujson.read(VectorStoreFileFixture.jsonCreateRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

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

    val jsonRequest: ujson.Value = ujson.read(VectorStoreFileFixture.jsonListRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Vector store file response" should "be properly deserialized from Json" in {
    import sttp.openai.requests.vectorstore.file.VectorStoreFileResponseData.VectorStoreFile._
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
    val serializedJson: Either[Exception, VectorStoreFile] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    serializedJson.value shouldBe givenResponse
  }

  "Vector store file response with error" should "be properly deserialized from Json" in {
    import sttp.openai.requests.vectorstore.file.VectorStoreFileResponseData.VectorStoreFile._
    // given
    val givenResponse = VectorStoreFile(
      id = "vsf_1",
      `object` = "vector_store.file",
      createdAt = 1698107661,
      usageBytes = 123456,
      status = Completed,
      vectorStoreId = "vs_1",
      lastError = Some(LastError(ServerError, "Failed"))
    )
    val jsonResponse = VectorStoreFileFixture.jsonObjectWithLastError

    // when
    val serializedJson: Either[Exception, VectorStoreFile] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    serializedJson.value shouldBe givenResponse
  }

  "Vector store file list response" should "be properly deserialized from Json" in {
    import sttp.openai.requests.vectorstore.file.VectorStoreFileResponseData.ListVectorStoreFilesResponse._
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
      lastError = Some(LastError(RateLimitExceeded, "Failed2"))
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
    val serializedJson: Either[Exception, ListVectorStoreFilesResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    serializedJson.value shouldBe givenResponse
  }

  "Delete of vector store file response" should "be properly deserialized from Json" in {
    import sttp.openai.requests.vectorstore.file.VectorStoreFileResponseData.DeleteVectorStoreFileResponse._
    // given

    val givenResponse = DeleteVectorStoreFileResponse(
      id = "file_abc123",
      `object` = "vector_store.file.deleted",
      deleted = true
    )

    val jsonResponse = VectorStoreFileFixture.jsonDelete

    // when
    val serializedJson: Either[Exception, DeleteVectorStoreFileResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    serializedJson.value shouldBe givenResponse
  }
}
