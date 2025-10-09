package sttp.openai.requests.audio.speech

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures.AudioFixture
import sttp.openai.json.SnakePickle
import sttp.openai.requests.audio.speech.SpeechModel.TTS1

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
    val jsonRequest: ujson.Value = ujson.read(AudioFixture.jsonCreateSpeechRequest)
    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)
    // then
    serializedJson shouldBe jsonRequest
  }

}
