package sttp.openai.requests.models

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures
import sttp.openai.requests.models.ModelsResponseData.ModelsResponse._
import sttp.openai.requests.models.ModelsResponseData.{DeletedModelData, ModelData, ModelsResponse}
import sttp.openai.utils.JsonUtils

class ModelsGetResponseDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given deleted model response as Json" should "be properly deserialized to case class" in {
    import ModelsResponseData.DeletedModelData._
    // given
    val response: String = """{
                             |  "id": "ft:gpt-4o-mini:acemeco:suffix:abc123",
                             |  "object": "model",
                             |  "deleted": true
                             |}""".stripMargin
    val expectedResponse: DeletedModelData = DeletedModelData(
      id = "ft:gpt-4o-mini:acemeco:suffix:abc123",
      `object` = "model",
      deleted = true
    )
    // when
    val givenResponse: Either[Exception, DeletedModelData] = JsonUtils.deserializeJsonSnake[DeletedModelData].apply(response)
    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given models response as Json" should "be properly deserialized to case class" in {

    // given
    val response: String = fixtures.ModelsGetResponse.responseJson

    val serializedData: Seq[ModelData] = Seq(
      ModelData(
        id = "babbage",
        `object` = "model",
        created = 1649358449,
        ownedBy = "openai"
      ),
      ModelData(
        id = "davinci",
        `object` = "model",
        created = 1649359874,
        ownedBy = "openai"
      )
    )

    val expectedResponse: ModelsResponse = ModelsResponse(`object` = "list", data = serializedData)
    // when

    val givenResponse: Either[Exception, ModelsResponse] = JsonUtils.deserializeJsonSnake.apply(response)

    // then
    givenResponse.value shouldBe expectedResponse
  }
}
