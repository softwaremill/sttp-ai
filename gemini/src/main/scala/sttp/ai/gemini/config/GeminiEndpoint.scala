package sttp.ai.gemini.config

/** Which Gemini API the client calls. It sets the path of the interactions resource under [[GeminiConfig.baseUrl]].
  *
  *   - [[GeminiEndpoint.DeveloperApi]]: the Gemini Developer API, `{baseUrl}/v1beta/interactions` (default).
  *   - [[GeminiEndpoint.Vertex]]: Vertex AI, `{baseUrl}/v1beta1/projects/{project}/locations/{location}/interactions`. Use
  *     [[GeminiConfig.vertex]], which also sets the matching host and authentication.
  */
sealed trait GeminiEndpoint {

  /** The path segments of the interactions collection. */
  def interactionsPath: List[String]
}

object GeminiEndpoint {
  case object DeveloperApi extends GeminiEndpoint {
    override val interactionsPath: List[String] = List("v1beta", "interactions")
  }

  /** @param project
    *   Google Cloud project ID
    * @param location
    *   Google Cloud location of the resource; only `global` serves the Interactions API while it is in Preview
    */
  case class Vertex(project: String, location: String) extends GeminiEndpoint {
    require(project.nonEmpty, "the Vertex AI project must not be empty")
    require(location.nonEmpty, "the Vertex AI location must not be empty")

    override val interactionsPath: List[String] = List("v1beta1", "projects", project, "locations", location, "interactions")
  }
}
