package sttp.ai.gemini.responses

/** Error body returned by the Gemini API, e.g. {"error": {"code": 400, "message": "...", "status": "INVALID_ARGUMENT"}}. `code` can be a
  * JSON string (e.g. "not_found") or a JSON number depending on the endpoint; it is normalized to a String here. `status` is not always
  * present.
  */
case class ErrorResponse(error: ErrorDetail)

case class ErrorDetail(
    code: Option[String] = None,
    message: String,
    status: Option[String] = None
)
