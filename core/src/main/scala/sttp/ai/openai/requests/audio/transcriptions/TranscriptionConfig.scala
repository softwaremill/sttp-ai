package sttp.openai.requests.audio.transcriptions

import sttp.openai.requests.images.ResponseFormat

import java.io.File
import java.nio.file.Paths

/** @param file
  *   The audio file to transcribe, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or webm.
  * @param model
  *   ID of the model to use. Only whisper-1 is currently available.
  * @param prompt
  *   An optional text to guide the model's style or continue a previous audio segment. The prompt should match the audio language.
  * @param responseFormat
  *   The format of the transcript output, one of `ResponseFormat`
  * @param temperature
  *   The sampling temperature, between 0 and 1. Higher values like 0.8 will make the output more random, while lower values like 0.2 will
  *   make it more focused and deterministic. If set to 0, the model will use log probability to automatically increase the temperature
  *   until certain thresholds are hit.
  * @param language
  *   The language of the input audio, use one of `Language`
  */
case class TranscriptionConfig(
    file: File,
    model: TranscriptionModel,
    prompt: Option[String] = None,
    responseFormat: Option[ResponseFormat] = None,
    temperature: Option[Float] = None,
    language: Option[Language] = None
)

object TranscriptionConfig {
  def createTranscriptionConfigWithSystemPaths(
      systemPathImage: String,
      model: TranscriptionModel,
      prompt: Option[String] = None,
      responseFormat: Option[ResponseFormat] = None,
      temperature: Option[Float] = None,
      language: Option[Language] = None
  ): TranscriptionConfig =
    TranscriptionConfig(
      Paths.get(systemPathImage).toFile,
      model,
      prompt,
      responseFormat,
      temperature,
      language
    )
}

abstract sealed class Language(val value: String)
object Language {
  case object Armenian extends Language("hy")

  case object Azerbaijani extends Language("az")

  case object Belarusian extends Language("be")

  case object Bosnian extends Language("bs")

  case object Bulgarian extends Language("bg")

  case object Catalan extends Language("ca")

  case object Chinese extends Language("zh")

  case object Croatian extends Language("hr")

  case object Czech extends Language("cs")

  case object Danish extends Language("da")

  case object Dutch extends Language("nl")

  case object English extends Language("en")

  case object Estonian extends Language("et")

  case object Finnish extends Language("fi")

  case object French extends Language("fr")

  case object Galician extends Language("gl")

  case object German extends Language("de")

  case object Greek extends Language("el")

  case object Hebrew extends Language("he")

  case object Hindi extends Language("hi")

  case object Hungarian extends Language("hu")

  case object Icelandic extends Language("is")

  case object Indonesian extends Language("id")

  case object Italian extends Language("it")

  case object Japanese extends Language("ja")

  case object Kannada extends Language("kn")

  case object Kazakh extends Language("kk")

  case object Korean extends Language("ko")

  case object Latvian extends Language("lv")

  case object Lithuanian extends Language("lt")

  case object Macedonian extends Language("mk")

  case object Malay extends Language("ms")

  case object Marathi extends Language("mr")

  case object Maori extends Language("mi")

  case object Nepali extends Language("ne")

  case object Norwegian extends Language("no")

  case object Persian extends Language("fa")

  case object Polish extends Language("pl")

  case object Portuguese extends Language("pt")

  case object Romanian extends Language("ro")

  case object Russian extends Language("ru")

  case object Serbian extends Language("sr")

  case object Slovak extends Language("sk")

  case object Slovenian extends Language("sl")

  case object Spanish extends Language("es")

  case object Swahili extends Language("sw")

  case object Swedish extends Language("sv")

  case object Tagalog extends Language("tl")

  case object Tamil extends Language("ta")

  case object Thai extends Language("th")

  case object Turkish extends Language("tr")

  case object Ukrainian extends Language("uk")

  case object Urdu extends Language("ur")

  case object Vietnamese extends Language("vi")

  case object Welsh extends Language("cy")

  /** @param customLanguage
    *   use ISO-639-1 format to use desired language
    */
  case class Custom(customLanguage: String) extends Language(customLanguage)
}
