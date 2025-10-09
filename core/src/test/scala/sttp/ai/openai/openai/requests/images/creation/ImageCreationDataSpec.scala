package sttp.openai.requests.images.creation

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures
import sttp.openai.json.SnakePickle
import sttp.openai.requests.images.{ResponseFormat, Size}
import sttp.openai.utils.JsonUtils
class ImageCreationDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given image generation response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.images.ImageResponseData.ImageResponse._
    import sttp.openai.requests.images.ImageResponseData._

    // given
    val jsonResponse = fixtures.ImageCreationFixture.jsonResponse

    val generatedImageData = Seq(
      GeneratedImageData("https://generated.image.url")
    )

    val expectedResponse: ImageResponse = ImageResponse(
      created = 1681893694,
      data = generatedImageData
    )
    // when
    val givenResponse = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given a fully populated ImageCreationBody" should "serialize to full JSON correctly" in {
    import sttp.openai.requests.images.creation.ImageCreationRequestBody.ImageCreationBody._
    import sttp.openai.requests.images.creation.ImageCreationRequestBody._

    // given
    val givenRequest: ImageCreationBody = ImageCreationBody(
      "cute fish", // prompt
      Some("transparent"), // background
      ImageCreationModel.DALLE3,
      Some("strict"), // moderation
      Some(1), // n
      Some(80), // outputCompression
      Some("png"), // outputFormat
      Some(2), // partialImages
      Some("high"), // quality
      Some(Size.Large), // size
      Some(ResponseFormat.URL), // responseFormat
      Some(false), // stream
      Some("vivid"), // style
      Some("user1") // user
    )

    val jsonRequest = ujson.read(fixtures.ImageCreationFixture.jsonRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given an ImageCreationBody with optional fields set to None" should "serialize without those fields" in {
    import sttp.openai.requests.images.creation.ImageCreationRequestBody.ImageCreationBody._
    import sttp.openai.requests.images.creation.ImageCreationRequestBody._

    // given
    val givenRequest: ImageCreationBody = ImageCreationBody(
      "cute fish", // prompt
      Some("transparent"), // background
      ImageCreationModel.DALLE3, // model
      None, // moderation
      Some(1), // n
      Some(80), // outputCompression
      Some("png"), // outputFormat
      None, // partialImages
      Some("high"), // quality
      Some(Size.Large), // size
      Some(ResponseFormat.URL), // responseFormat
      Some(false), // stream
      Some("vivid"), // style
      Some("user1") // user
    )

    val jsonRequest = ujson.read(fixtures.ImageCreationFixture.jsonRequestWithSomeOptionalsSetToNone)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create image request as case class created with enum values" should "be properly serialized to Json" in {
    import sttp.openai.requests.images.creation.ImageCreationRequestBody.ImageCreationBody._
    import sttp.openai.requests.images.creation.ImageCreationRequestBody._

    // given
    val givenRequest: ImageCreationBody = ImageCreationBody(
      "cute fish", // prompt
      Some("transparent"), // background
      ImageCreationModel.DALLE3, // model
      Some("strict"), // moderation
      Some(1), // n
      Some(80), // outputCompression
      Some("png"), // outputFormat
      Some(2), // partialImages
      Some("high"), // quality
      Some(Size.Large), // size
      Some(ResponseFormat.URL), // responseFormat
      Some(false), // stream
      Some("vivid"), // style
      Some("user1") // user
    )

    val jsonRequest = ujson.read(fixtures.ImageCreationFixture.jsonRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create image request as case class with different model" should "be properly serialized to Json" in {
    import sttp.openai.requests.images.creation.ImageCreationRequestBody.ImageCreationBody._
    import sttp.openai.requests.images.creation.ImageCreationRequestBody._

    // given
    val givenRequest: ImageCreationBody = ImageCreationBody(
      "cute fish", // prompt
      Some("transparent"), // background
      ImageCreationModel.DALLE2,
      Some("strict"), // moderation
      Some(1), // n
      Some(80), // outputCompression
      Some("png"), // outputFormat
      Some(2), // partialImages
      Some("high"), // quality
      Some(Size.Large), // size
      Some(ResponseFormat.URL), // responseFormat
      Some(false), // stream
      Some("vivid"), // style
      Some("user1") // user
    )

    val jsonRequest = ujson.read(fixtures.ImageCreationFixture.jsonRequestDalle2)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }
}
