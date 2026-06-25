package sttp.ai.openai.requests.audio.speech

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

abstract sealed class SpeechModel(val value: String)

object SpeechModel {
  case object GPT4oMiniTTS extends SpeechModel("gpt-4o-mini-tts")
  case object TTS1 extends SpeechModel("tts-1")
  case object TTS1HD extends SpeechModel("tts-1-hd")
  case class CustomSpeechModel(customValue: String) extends SpeechModel(customValue)
}

sealed trait Voice

object Voice {
  sealed trait Standard extends Voice
  case object Alloy extends Standard
  case object Ash extends Standard
  case object Coral extends Standard
  case object Echo extends Standard
  case object Fable extends Standard
  case object Onyx extends Standard
  case object Nova extends Standard
  case object Sage extends Standard
  case object Shimmer extends Standard
  case class CustomVoice(customVoice: String) extends Voice
}

sealed trait ResponseFormat

object ResponseFormat {
  sealed trait Standard extends ResponseFormat
  case object Mp3 extends Standard
  case object Opus extends Standard
  case object Aac extends Standard
  case object Flac extends Standard
  case object Wav extends Standard
  case object Pcm extends Standard
  case class CustomFormat(customFormat: String) extends ResponseFormat
}
