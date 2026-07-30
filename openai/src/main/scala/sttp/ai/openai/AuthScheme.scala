package sttp.ai.openai

/** Controls how the API key is attached to outgoing requests.
  *
  *   - [[AuthScheme.Bearer]] sends `Authorization: Bearer <key>` — OpenAI and most compatible providers (default).
  *   - [[AuthScheme.AzureApiKey]] sends `api-key: <key>` — Azure OpenAI deployment endpoints.
  */
sealed trait AuthScheme {

  /** The header (name -> value) carrying the given API key under this scheme. */
  def authHeader(apiKey: String): (String, String)
}

object AuthScheme {
  case object Bearer extends AuthScheme {
    override def authHeader(apiKey: String): (String, String) = "Authorization" -> s"Bearer $apiKey"
  }
  case object AzureApiKey extends AuthScheme {
    override def authHeader(apiKey: String): (String, String) = "api-key" -> apiKey
  }
}
