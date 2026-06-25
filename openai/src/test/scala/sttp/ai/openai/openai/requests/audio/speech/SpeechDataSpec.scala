package sttp.ai.openai.requests.audio.speech

import io.circe.parser.parse
import io.circe.syntax._
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.json.OpenAIManualCodecs._

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.fixtures.AudioFixture
import sttp.ai.openai.requests.audio.speech.SpeechModel.TTS1

class SpeechDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given create fine tuning job request as case class" should "be properly serialized to Json" in {
    // given
    val givenRequest = SpeechRequestBody(
      model = TTS1,
      input = "Hello, my name is John.",
      voice = Voice.Alloy,
      responseFormat = Some(ResponseFormat.Mp3),
      speed = Some(1.0f)
    )
    val jsonRequest: io.circe.Json = parse(AudioFixture.jsonCreateSpeechRequest).value
    // when
    val serializedJson: io.circe.Json = givenRequest.asJson.deepDropNullValues
    // then
    serializedJson shouldBe jsonRequest
  }

}
