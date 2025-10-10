package sttp.ai.openai.requests.responses

import sttp.ai.core.json.SnakePickle

case class DeleteModelResponseResponse(
    `object`: String,
    id: String,
    deleted: Boolean
)

object DeleteModelResponseResponse {
  implicit val deleteModelResponseResponseR: SnakePickle.Reader[DeleteModelResponseResponse] = SnakePickle.macroR
}
