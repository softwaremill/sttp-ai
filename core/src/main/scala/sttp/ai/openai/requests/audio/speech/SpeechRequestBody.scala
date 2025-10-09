package sttp.openai.requests.audio.speech

import sttp.openai.json.SnakePickle

/** Represents the request body for generating speech from text.
  *
  * @param model
  *   One of the available TTS models: tts-1 or tts-1-hd.
  * @param input
  *   The text to generate audio for. The maximum length is 4096 characters.
  * @param voice
  *   The voice to use when generating the audio. Supported voices are alloy, ash, coral, echo, fable, onyx, nova, sage, and shimmer.
  *   Previews of the voices are available in the Text to speech guide
  *   [[https://platform.openai.com/docs/guides/text-to-speech#voice-options]].
  * @param responseFormat
  *   The format to audio in. Supported formats are mp3, opus, aac, flac, wav, and pcm. Defaults to mp3.
  * @param speed
  *   The speed of the generated audio. Select a value from 0.25 to 4.0. 1.0 is the default.
  */
case class SpeechRequestBody(
    model: SpeechModel,
    input: String,
    voice: Voice,
    responseFormat: Option[ResponseFormat] = None,
    speed: Option[Float] = None
)

object SpeechRequestBody {
  implicit val speechRequestBodyW: SnakePickle.Writer[SpeechRequestBody] = SnakePickle.macroW[SpeechRequestBody]
}

abstract sealed class SpeechModel(val value: String)

object SpeechModel {
  implicit val speechModelW: SnakePickle.Writer[SpeechModel] = SnakePickle
    .writer[ujson.Value]
    .comap[SpeechModel](_.value)

  case object GPT4oMiniTTS extends SpeechModel("gpt-4o-mini-tts")
  case object TTS1 extends SpeechModel("tts-1")
  case object TTS1HD extends SpeechModel("tts-1-hd")
  case class CustomSpeechModel(customValue: String) extends SpeechModel(customValue)
}

sealed abstract class Voice(val value: String)

object Voice {
  case object Alloy extends Voice("alloy")
  case object Ash extends Voice("ash")
  case object Coral extends Voice("coral")
  case object Echo extends Voice("echo")
  case object Fable extends Voice("fable")
  case object Onyx extends Voice("onyx")
  case object Nova extends Voice("nova")
  case object Sage extends Voice("sage")
  case object Shimmer extends Voice("shimmer")
  case class CustomVoice(customVoice: String) extends Voice(customVoice)

  implicit val voiceW: SnakePickle.Writer[Voice] = SnakePickle
    .writer[ujson.Value]
    .comap[Voice](_.value)
}

sealed abstract class ResponseFormat(val value: String)

object ResponseFormat {
  case object Mp3 extends ResponseFormat("mp3")
  case object Opus extends ResponseFormat("opus")
  case object Aac extends ResponseFormat("aac")
  case object Flac extends ResponseFormat("flac")
  case object Wav extends ResponseFormat("wav")
  case object Pcm extends ResponseFormat("pcm")
  case class CustomFormat(customFormat: String) extends ResponseFormat(customFormat)

  implicit val formatW: SnakePickle.Writer[ResponseFormat] = SnakePickle
    .writer[ujson.Value]
    .comap[ResponseFormat](_.value)
}
