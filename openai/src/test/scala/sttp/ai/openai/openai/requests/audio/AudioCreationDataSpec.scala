package sttp.ai.openai.requests.audio

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.fixtures
import io.circe.parser.decode
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.json.OpenAIManualCodecs._

class AudioCreationDataSpec extends AnyFlatSpec with Matchers with EitherValues {
  "Given audio generation response as Json" should "be properly deserialized to case class" in {
    import sttp.ai.openai.requests.audio.AudioResponseData.AudioResponse
    import sttp.ai.openai.requests.audio.AudioResponseData.AudioResponse._

    // given
    val jsonResponse = fixtures.AudioFixture.jsonResponse

    val expectedResponse = AudioResponse(
      "Imagine the wildest idea that you've ever had, and you're curious about how it might scale to something that's a 100, a 1,000 times bigger. This is a place where you can get to do that."
    )

    // when
    val deserializedJsonResponse = decode[AudioResponse](jsonResponse)

    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

}
