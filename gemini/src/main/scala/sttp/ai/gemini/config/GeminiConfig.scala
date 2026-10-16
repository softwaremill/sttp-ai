package sttp.ai.gemini.config

import sttp.ai.core.config.AIClientConfig
import sttp.model.Uri

import scala.concurrent.duration.Duration

/** Configuration for the Google Gemini (Interactions API) client.
  *
  * @param apiKey
  *   Gemini API key for authentication (not used with [[GeminiAuth.AccessToken]])
  * @param baseUrl
  *   Base URL for the Gemini API (defaults to the Developer API; the `endpoint` path is appended by the client)
  * @param timeout
  *   Request timeout duration, applied to every request (defaults to 10 minutes)
  * @param maxRetries
  *   Maximum number of retry attempts for transient failures, honored by the sync client (defaults to 3)
  * @param organization
  *   Optional organization identifier (unused by the Gemini API, present for [[AIClientConfig]] parity)
  * @param endpoint
  *   The API the client calls: the Developer API (default) or Vertex AI. See [[GeminiConfig.vertex]] for Vertex AI.
  * @param auth
  *   How requests are authenticated: the API key (default) or an OAuth 2 access token
  */
case class GeminiConfig(
    apiKey: String,
    baseUrl: Uri = GeminiConfig.DefaultBaseUrl,
    timeout: Duration = GeminiConfig.DefaultTimeout,
    maxRetries: Int = GeminiConfig.DefaultMaxRetries,
    organization: Option[String] = None,
    endpoint: GeminiEndpoint = GeminiEndpoint.DeveloperApi,
    auth: GeminiAuth = GeminiAuth.ApiKey
) extends AIClientConfig {

  override def authHeaders: Map[String, String] = Map(
    auth.authHeader(apiKey),
    "content-type" -> "application/json"
  )
}

object GeminiConfig {
  val DefaultBaseUrl: Uri = Uri.unsafeParse("https://generativelanguage.googleapis.com")

  /** The host of the `global` location of Vertex AI. */
  val VertexBaseUrl: Uri = Uri.unsafeParse("https://aiplatform.googleapis.com")

  /** The Vertex AI host of a location: [[VertexBaseUrl]] for `global`, `https://{location}-aiplatform.googleapis.com` for a region. */
  def vertexBaseUrl(location: String): Uri =
    if (location == "global") VertexBaseUrl else Uri.unsafeParse(s"https://$location-aiplatform.googleapis.com")

  /** Matches the default request timeout of the official OpenAI/Anthropic SDKs. */
  val DefaultTimeout: Duration = AIClientConfig.DefaultTimeout
  val DefaultMaxRetries: Int = AIClientConfig.DefaultMaxRetries

  /** Configuration for the Interactions API on Vertex AI, authenticated with an OAuth 2 access token. `apiKey` is empty: [[AIClientConfig]]
    * requires it, and [[GeminiAuth.AccessToken]] does not use it.
    *
    * @param project
    *   Google Cloud project ID
    * @param accessToken
    *   Called each time the client builds a request, e.g.
    *   `() => { credentials.refreshIfExpired(); credentials.getAccessToken.getTokenValue }` with google-auth-library's `GoogleCredentials`
    * @param location
    *   Google Cloud location (defaults to `global`, the only one that serves the Interactions API while it is in Preview)
    * @param baseUrl
    *   The host, when it is not the one [[vertexBaseUrl]] gives for the location (e.g. a proxy)
    */
  def vertex(
      project: String,
      accessToken: () => String,
      location: String = "global",
      baseUrl: Option[Uri] = None,
      timeout: Duration = DefaultTimeout,
      maxRetries: Int = DefaultMaxRetries
  ): GeminiConfig =
    GeminiConfig(
      apiKey = "",
      baseUrl = baseUrl.getOrElse(vertexBaseUrl(location)),
      timeout = timeout,
      maxRetries = maxRetries,
      endpoint = GeminiEndpoint.Vertex(project, location),
      auth = GeminiAuth.AccessToken(accessToken)
    )

  /** Creates GeminiConfig from environment variables.
    *
    * Required environment variables:
    *   - GEMINI_API_KEY: Gemini API key
    *
    * Optional environment variables:
    *   - GEMINI_BASE_URL: Custom base URL (defaults to the official endpoint)
    */
  def fromEnv: GeminiConfig = {
    val apiKey =
      sys.env.getOrElse("GEMINI_API_KEY", throw new IllegalArgumentException("GEMINI_API_KEY environment variable is required"))
    val baseUrl = sys.env.get("GEMINI_BASE_URL").map(Uri.unsafeParse).getOrElse(DefaultBaseUrl)

    GeminiConfig(apiKey = apiKey, baseUrl = baseUrl)
  }
}
