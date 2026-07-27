package sttp.ai.gemini.config

import sttp.ai.core.config.AIClientConfig
import sttp.model.Uri

import scala.concurrent.duration.{Duration, DurationInt}

/** Configuration for the Google Gemini (Interactions API) client.
  *
  * @param apiKey
  *   Gemini API key for authentication
  * @param baseUrl
  *   Base URL for the Gemini API (defaults to the official endpoint; the `v1beta` version path is appended by the client)
  * @param timeout
  *   Request timeout duration (defaults to 60 seconds)
  * @param maxRetries
  *   Maximum number of retry attempts (defaults to 3)
  * @param organization
  *   Optional organization identifier (unused by the Gemini API, present for [[AIClientConfig]] parity)
  */
case class GeminiConfig(
    apiKey: String,
    baseUrl: Uri = GeminiConfig.DefaultBaseUrl,
    timeout: Duration = 60.seconds,
    maxRetries: Int = 3,
    organization: Option[String] = None
) extends AIClientConfig {

  override def authHeaders: Map[String, String] = Map(
    "x-goog-api-key" -> apiKey,
    "content-type" -> "application/json"
  )
}

object GeminiConfig {
  val DefaultBaseUrl: Uri = Uri.unsafeParse("https://generativelanguage.googleapis.com")

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
