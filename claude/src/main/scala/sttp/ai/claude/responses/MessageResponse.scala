package sttp.ai.claude.responses

import sttp.ai.claude.models.{ContentBlock, Usage}
import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}

case class MessageResponse(
    id: String,
    `type`: String,
    role: String,
    content: List[ContentBlock],
    model: String,
    stopReason: Option[String],
    stopSequence: Option[String],
    usage: Usage
)

object MessageResponse {
  implicit val rw: ReadWriter[MessageResponse] = macroRW
}

case class ErrorResponse(
    error: ErrorDetail
)

case class ErrorDetail(
    `type`: String,
    message: String
)

object ErrorDetail {
  implicit val rw: ReadWriter[ErrorDetail] = macroRW
}

object ErrorResponse {
  implicit val rw: ReadWriter[ErrorResponse] = macroRW
}
