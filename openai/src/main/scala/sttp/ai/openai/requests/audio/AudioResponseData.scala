package sttp.ai.openai.requests.audio

import sttp.ai.core.json.SnakePickle

object AudioResponseData {

  case class AudioResponse(text: String)

  object AudioResponse {
    implicit val audioResponseR: SnakePickle.Reader[AudioResponse] = SnakePickle.macroR[AudioResponse]
  }

}
