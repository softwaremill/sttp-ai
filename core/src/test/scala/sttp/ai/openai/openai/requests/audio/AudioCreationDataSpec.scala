package sttp.openai.requests.audio

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures
import sttp.openai.utils.JsonUtils

class AudioCreationDataSpec extends AnyFlatSpec with Matchers with EitherValues {
  "Given audio generation response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.audio.AudioResponseData.AudioResponse
    import sttp.openai.requests.audio.AudioResponseData.AudioResponse._

    // given
    val jsonResponse = fixtures.AudioFixture.jsonResponse

    val expectedResponse = AudioResponse(
      "Imagine the wildest idea that you've ever had, and you're curious about how it might scale to something that's a 100, a 1,000 times bigger. This is a place where you can get to do that."
    )

    // when
    val deserializedJsonResponse = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

}
