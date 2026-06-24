package sttp.ai.openai.requests.images

sealed trait ResponseFormat

object ResponseFormat {

  sealed trait Standard extends ResponseFormat

  case object URL extends Standard

  case object B64Json extends Standard

  /** Use only as a workaround if API supports a format that's not yet predefined as a case object of Response Format. Otherwise, a custom
    * format would be rejected. See [[https://platform.openai.com/docs/api-reference/images/create-edit]] for current list of supported
    * formats
    */
  case class Custom(customResponseFormat: String) extends ResponseFormat

  /** The wire string for this format, used for multipart form encoding (non-JSON). Matches the snake_case JSON codec. */
  def asString(responseFormat: ResponseFormat): String = responseFormat match {
    case URL          => "url"
    case B64Json      => "b64_json"
    case Custom(name) => name
  }
}
