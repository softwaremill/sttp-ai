package sttp.openai.requests.audio

import sttp.openai.json.SnakePickle

object AudioResponseData {

  case class AudioResponse(text: String)

  object AudioResponse {
    implicit val audioResponseR: SnakePickle.Reader[AudioResponse] = SnakePickle.macroR[AudioResponse]
  }

}
