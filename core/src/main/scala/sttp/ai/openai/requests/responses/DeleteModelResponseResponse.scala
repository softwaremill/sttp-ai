package sttp.openai.requests.responses

import sttp.openai.json.SnakePickle

case class DeleteModelResponseResponse(
    `object`: String,
    id: String,
    deleted: Boolean
)

object DeleteModelResponseResponse {
  implicit val deleteModelResponseResponseR: SnakePickle.Reader[DeleteModelResponseResponse] = SnakePickle.macroR
}
