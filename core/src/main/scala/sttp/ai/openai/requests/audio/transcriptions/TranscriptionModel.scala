package sttp.openai.requests.audio.transcriptions

import sttp.openai.json.SnakePickle

sealed abstract class TranscriptionModel(val value: String)

object TranscriptionModel {
  case object GPT4oMiniTranscribe extends TranscriptionModel("gpt-4o-mini-transcribe")
  case object GPT4oTranscribe extends TranscriptionModel("gpt-4o-transcribe")
  case object Whisper1 extends TranscriptionModel("whisper-1")

  /** Use only as a workaround if API supports a format that's not yet predefined as a case object of Model. Otherwise, a custom format
    * would be rejected. See [[https://platform.openai.com/docs/api-reference/audio/createTranscription]] for current list of supported
    * formats
    */
  case class Custom(customModel: String) extends TranscriptionModel(customModel)

  implicit val ModelW: SnakePickle.Writer[TranscriptionModel] = SnakePickle
    .writer[ujson.Value]
    .comap(_.value)
}
