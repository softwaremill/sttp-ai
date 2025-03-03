package sttp.openai.requests.admin

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures.AdminFixture
import sttp.openai.json.{SnakePickle, SttpUpickleApiExtension}

class AdminApiKeyDataSpec extends AnyFlatSpec with Matchers with EitherValues {
  "Given create admin api key request as case class" should "be properly serialized to Json" in {
    // given
    val givenRequest = AdminApiKeyRequestBody(
      name = "api_key_name"
    )
    val jsonRequest: ujson.Value = ujson.read(AdminFixture.jsonRequest)
    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)
    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create admin api key response as Json" should "be properly deserialized to case class" in {
    // given
    val jsonResponse = AdminFixture.jsonResponse
    val expectedResponse: AdminApiKeyResponse = AdminFixture.adminApiKeyResponse
    // when
    val deserializedJsonResponse: Either[Exception, AdminApiKeyResponse] =
      SttpUpickleApiExtension.deserializeJsonSnake[AdminApiKeyResponse].apply(jsonResponse)
    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

  "Given list admin api key response as Json" should "be properly deserialized to case class" in {
    // given
    val jsonResponse = AdminFixture.jsonListResponse
    val expectedResponse: ListAdminApiKeyResponse = ListAdminApiKeyResponse(
      data = Seq(AdminFixture.adminApiKeyResponse),
      hasMore = false,
      firstId = "key_abc",
      lastId = "key_abc"
    )
    // when
    val deserializedJsonResponse: Either[Exception, ListAdminApiKeyResponse] =
      SttpUpickleApiExtension.deserializeJsonSnake[ListAdminApiKeyResponse].apply(jsonResponse)
    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

  "Given delete admin api key response as Json" should "be properly deserialized to case class" in {
    // given
    val jsonResponse = AdminFixture.jsonDeleteResponse
    val expectedResponse: DeleteAdminApiKeyResponse = DeleteAdminApiKeyResponse(
      id = "key_abc",
      deleted = true
    )
    // when
    val deserializedJsonResponse: Either[Exception, DeleteAdminApiKeyResponse] =
      SttpUpickleApiExtension.deserializeJsonSnake[DeleteAdminApiKeyResponse].apply(jsonResponse)
    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

}
