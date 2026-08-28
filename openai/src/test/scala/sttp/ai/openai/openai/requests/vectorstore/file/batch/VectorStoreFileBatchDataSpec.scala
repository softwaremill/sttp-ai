package sttp.ai.openai.requests.vectorstore.file.batch

import io.circe.parser.{decode, parse}
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.fixtures.VectorStoreFileBatchFixture
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.json.OpenAIManualCodecs._
import sttp.ai.openai.requests.vectorstore.VectorStoreResponseData.FileCounts
import sttp.ai.openai.requests.vectorstore.file.{Completed, InProgress}
import sttp.ai.openai.requests.vectorstore.file.batch.VectorStoreFileBatchRequestBody.CreateVectorStoreFileBatchBody
import sttp.ai.openai.requests.vectorstore.file.batch.VectorStoreFileBatchResponseData.VectorStoreFileBatch

class VectorStoreFileBatchDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given create vector store file batch request" should "be properly serialized to Json" in {
    // given
    val givenRequest = CreateVectorStoreFileBatchBody(fileIds = Seq("file_1", "file_2"))
    val jsonRequest: io.circe.Json = parse(VectorStoreFileBatchFixture.jsonCreateRequest).value

    // when
    val serializedJson: io.circe.Json = givenRequest.asJson.deepDropNullValues

    // then
    serializedJson shouldBe jsonRequest
  }

  "In-progress vector store file batch response" should "be properly deserialized from Json" in {
    // given
    val givenResponse = VectorStoreFileBatch(
      id = "vsfb_1",
      `object` = "vector_store.file_batch",
      createdAt = 1698107661,
      vectorStoreId = "vs_1",
      status = InProgress,
      fileCounts = FileCounts(inProgress = 2, completed = 0, failed = 0, cancelled = 0, total = 2)
    )

    // when
    val deserialized: Either[Exception, VectorStoreFileBatch] =
      decode[VectorStoreFileBatch](VectorStoreFileBatchFixture.jsonObjectInProgress)

    // then
    deserialized.value shouldBe givenResponse
  }

  "Completed vector store file batch response" should "be properly deserialized from Json" in {
    // given
    val givenResponse = VectorStoreFileBatch(
      id = "vsfb_1",
      `object` = "vector_store.file_batch",
      createdAt = 1698107661,
      vectorStoreId = "vs_1",
      status = Completed,
      fileCounts = FileCounts(inProgress = 0, completed = 1, failed = 1, cancelled = 0, total = 2)
    )

    // when
    val deserialized: Either[Exception, VectorStoreFileBatch] =
      decode[VectorStoreFileBatch](VectorStoreFileBatchFixture.jsonObjectCompleted)

    // then
    deserialized.value shouldBe givenResponse
  }

}
