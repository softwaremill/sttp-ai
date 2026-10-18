package sttp.ai.gemini.config

/** Which Gemini API the client calls, and how requests are authenticated. */
sealed trait GeminiApi {

  /** The path segments of the interactions collection, under [[GeminiConfig.baseUrl]]. */
  def interactionsPath: List[String]

  /** The header (name -> value) that authenticates a request, given the config's API key. */
  def authHeader(apiKey: String): (String, String)
}

object GeminiApi {

  /** The Gemini Developer API, authenticated with the config's API key (`x-goog-api-key`). */
  case object DeveloperApi extends GeminiApi {
    override val interactionsPath: List[String] = List("v1beta", "interactions")
    override def authHeader(apiKey: String): (String, String) = "x-goog-api-key" -> apiKey
  }

  /** Vertex AI, authenticated with an OAuth 2 access token (`Authorization: Bearer`); the config's API key is not used. Use
    * [[GeminiConfig.vertex]], which also sets the matching host.
    *
    * @param project
    *   Google Cloud project ID
    * @param accessToken
    *   Called each time the client builds a request; the token is then fixed in that request, including its retries. Return a refreshed
    *   token, and build a new request for each send.
    */
  case class Vertex(project: String, accessToken: () => String) extends GeminiApi {
    require(project.nonEmpty, "the Vertex AI project must not be empty")

    // Only the global location serves the Interactions API while it is in Preview.
    override val interactionsPath: List[String] = List("v1beta1", "projects", project, "locations", "global", "interactions")
    override def authHeader(apiKey: String): (String, String) = "Authorization" -> s"Bearer ${accessToken()}"
  }
}
