package sttp.openai.requests.images

import sttp.openai.json.SnakePickle

sealed abstract class ResponseFormat(val value: String)

object ResponseFormat {
  case object URL extends ResponseFormat("url")

  case object B64Json extends ResponseFormat("b64_json")

  /** Use only as a workaround if API supports a format that's not yet predefined as a case object of Response Format. Otherwise, a custom
    * format would be rejected. See [[https://platform.openai.com/docs/api-reference/images/create-edit]] for current list of supported
    * formats
    */
  case class Custom(customResponseFormat: String) extends ResponseFormat(customResponseFormat)

  val values: Set[ResponseFormat] = Set(URL, B64Json)

  implicit val responseFormatW: SnakePickle.Writer[ResponseFormat] = SnakePickle
    .writer[ujson.Value]
    .comap[ResponseFormat](_.value)
}
