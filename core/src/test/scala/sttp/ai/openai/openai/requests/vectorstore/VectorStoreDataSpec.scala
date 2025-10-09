package sttp.openai.requests.vectorstore

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures.VectorStoreFixture
import sttp.openai.json.SnakePickle
import sttp.openai.requests.vectorstore.VectorStoreRequestBody.{CreateVectorStoreBody, ModifyVectorStoreBody}
import sttp.openai.requests.vectorstore.VectorStoreResponseData._
import sttp.openai.utils.JsonUtils

class VectorStoreDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given create vector store request" should "be properly serialized to Json" in {
    // given
    val givenRequest = CreateVectorStoreBody(
      fileIds = Some(Seq("file_1", "file_2")),
      name = Some("vs_1")
    )

    val jsonRequest: ujson.Value = ujson.read(VectorStoreFixture.jsonCreateRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create vector store request with expires" should "be properly serialized to Json" in {
    // given
    val givenRequest = CreateVectorStoreBody(
      fileIds = Some(Seq("file_1", "file_2")),
      name = Some("vs_1"),
      expiresAfter = Some(ExpiresAfter("11111", 2))
    )

    val jsonRequest: ujson.Value = ujson.read(VectorStoreFixture.jsonCreateWithExpiresRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given modify vector store request" should "be properly serialized to Json" in {
    // given
    val givenRequest = ModifyVectorStoreBody(
      name = Some("vs_3"),
      expiresAfter = Some(ExpiresAfter("2322", 5))
    )

    val jsonRequest: ujson.Value = ujson.read(VectorStoreFixture.jsonModify)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Vector store object" should "be properly deserialized from Json" in {
    import sttp.openai.requests.vectorstore.VectorStoreResponseData.VectorStore._
    // given
    val givenResponse = VectorStore(
      id = "vs_1",
      `object` = "vector_store",
      createdAt = 1698107661,
      name = "test_vs",
      usageBytes = 123456,
      fileCounts = FileCounts(0, 1, 1, 2, 4),
      status = InProgress,
      expiresAfter = None,
      expiresAt = Some(1698107651),
      lastActiveAt = Some(1698107661),
      lastUsedAt = Some(1698107681)
    )
    val jsonResponse = VectorStoreFixture.jsonObject

    // when
    val serializedJson: Either[Exception, VectorStore] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    serializedJson.value shouldBe givenResponse
  }

  "List of vector stores" should "be properly deserialized from Json" in {
    import sttp.openai.requests.vectorstore.VectorStoreResponseData.ListVectorStoresResponse._
    // given

    val first = VectorStore(
      id = "vs_abc123",
      `object` = "vector_store",
      createdAt = 1699061776,
      name = "Support FAQ",
      usageBytes = 139920,
      status = Completed,
      fileCounts = FileCounts(0, 3, 0, 0, 3)
    )

    val second = VectorStore(
      id = "vs_abc456",
      `object` = "vector_store",
      createdAt = 1699061776,
      name = "Support FAQ v2",
      usageBytes = 139921,
      status = InProgress,
      fileCounts = FileCounts(1, 2, 2, 1, 6)
    )
    val givenResponse = ListVectorStoresResponse(
      `object` = "list",
      data = Seq(first, second),
      firstId = "vs_abc123",
      lastId = "vs_abc456",
      hasMore = false
    )

    val jsonResponse = VectorStoreFixture.jsonList

    // when
    val serializedJson: Either[Exception, ListVectorStoresResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    serializedJson.value shouldBe givenResponse
  }

  "Delete of vector stores response" should "be properly deserialized from Json" in {
    import sttp.openai.requests.vectorstore.VectorStoreResponseData.DeleteVectorStoreResponse._
    // given

    val givenResponse = DeleteVectorStoreResponse(
      id = "vs_abc123",
      `object` = "vector_store.deleted",
      deleted = true
    )

    val jsonResponse = VectorStoreFixture.jsonDelete

    // when
    val serializedJson: Either[Exception, DeleteVectorStoreResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    serializedJson.value shouldBe givenResponse
  }
}
