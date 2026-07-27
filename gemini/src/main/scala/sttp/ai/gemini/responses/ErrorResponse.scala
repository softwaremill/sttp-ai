package sttp.ai.gemini.responses

/** Error body returned by the Gemini API, e.g. {"error": {"code": 400, "message": "...", "status": "INVALID_ARGUMENT"}}. */
case class ErrorResponse(error: ErrorDetail)

case class ErrorDetail(
    code: Option[Int] = None,
    message: String,
    status: Option[String] = None
)
