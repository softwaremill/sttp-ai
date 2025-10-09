package sttp.openai.requests.audio.translations

import sttp.openai.json.SnakePickle

sealed abstract class TranslationModel(val value: String)

object TranslationModel {
  case object Whisper1 extends TranslationModel("whisper-1")

  /** Use only as a workaround if API supports a format that's not yet predefined as a case object of Model. Otherwise, a custom format
    * would be rejected. See [[https://platform.openai.com/docs/api-reference/audio/createTranslation]] for current list of supported
    * formats
    */
  case class Custom(customModel: String) extends TranslationModel(customModel)

  implicit val ModelW: SnakePickle.Writer[TranslationModel] = SnakePickle
    .writer[ujson.Value]
    .comap(_.value)
}
