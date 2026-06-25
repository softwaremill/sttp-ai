package sttp.ai.openai.requests.responses

case class DeleteModelResponseResponse(
    `object`: String,
    id: String,
    deleted: Boolean
)
