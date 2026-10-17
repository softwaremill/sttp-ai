package sttp.ai.gemini.config

/** Controls how requests are authenticated.
  *
  *   - [[GeminiAuth.ApiKey]] sends `x-goog-api-key: <apiKey>` — the Gemini Developer API (default).
  *   - [[GeminiAuth.AccessToken]] sends `Authorization: Bearer <token>` — Vertex AI with an OAuth 2 access token, e.g. from Application
  *     Default Credentials. The config's `apiKey` is not used.
  */
sealed trait GeminiAuth {

  /** The header (name -> value) that authenticates a request, given the config's API key. */
  def authHeader(apiKey: String): (String, String)
}

object GeminiAuth {
  case object ApiKey extends GeminiAuth {
    override def authHeader(apiKey: String): (String, String) = "x-goog-api-key" -> apiKey
  }

  /** @param token
    *   Called each time the client builds a request, on the thread that builds it; a retry of that request sends the same token. Access
    *   tokens typically expire after an hour, so return a refreshed one. An exception it throws propagates from the client method.
    */
  case class AccessToken(token: () => String) extends GeminiAuth {
    override def authHeader(apiKey: String): (String, String) = "Authorization" -> s"Bearer ${token()}"
  }
}
