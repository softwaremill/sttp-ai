package sttp.ai.claude.responses

import sttp.ai.claude.models.{ContentBlock, Usage}

case class MessageResponse(
    id: String,
    `type`: String,
    role: String,
    content: List[ContentBlock],
    model: String,
    stopReason: Option[String] = None,
    stopSequence: Option[String] = None,
    usage: Usage
)

case class ErrorResponse(
    error: ErrorDetail
)

case class ErrorDetail(
    `type`: String,
    message: String
)
