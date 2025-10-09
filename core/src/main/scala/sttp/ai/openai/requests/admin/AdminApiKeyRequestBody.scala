package sttp.openai.requests.admin

import sttp.openai.json.SnakePickle

/** Create an organization admin API key
  */
case class AdminApiKeyRequestBody(name: String) {}

object AdminApiKeyRequestBody {
  implicit val adminApiKeyRequestBodyW: SnakePickle.Writer[AdminApiKeyRequestBody] = SnakePickle.macroW[AdminApiKeyRequestBody]
}
