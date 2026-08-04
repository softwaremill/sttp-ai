package sttp.ai.claude.config

import sttp.ai.core.config.AIClientConfig
import sttp.model.Uri

import scala.concurrent.duration.Duration

/** Configuration for Claude (Anthropic) API client.
  *
  * @param apiKey
  *   Anthropic API key for authentication
  * @param anthropicVersion
  *   Anthropic API version (defaults to "2023-06-01")
  * @param baseUrl
  *   Base URL for Anthropic API (defaults to official Anthropic endpoint)
  * @param timeout
  *   Request timeout duration, applied to every request (defaults to 10 minutes)
  * @param maxRetries
  *   Maximum number of retry attempts for transient failures, honored by the sync client (defaults to 3)
  * @param organization
  *   Optional organization identifier
  */
case class ClaudeConfig(
    apiKey: String,
    anthropicVersion: String = ClaudeConfig.DefaultApiVersion,
    baseUrl: Uri = ClaudeConfig.DefaultBaseUrl,
    timeout: Duration = ClaudeConfig.DefaultTimeout,
    maxRetries: Int = ClaudeConfig.DefaultMaxRetries,
    organization: Option[String] = None
) extends AIClientConfig {

  override def authHeaders: Map[String, String] = Map(
    "x-api-key" -> apiKey,
    "anthropic-version" -> anthropicVersion,
    "content-type" -> "application/json"
  )
}

object ClaudeConfig {
  val DefaultBaseUrl: Uri = Uri.unsafeParse("https://api.anthropic.com")
  val DefaultApiVersion = "2023-06-01"

  /** Matches the default request timeout of the official OpenAI/Anthropic SDKs. */
  val DefaultTimeout: Duration = AIClientConfig.DefaultTimeout
  val DefaultMaxRetries: Int = AIClientConfig.DefaultMaxRetries

  /** Creates ClaudeConfig from environment variables.
    *
    * Required environment variables:
    *   - ANTHROPIC_API_KEY: Anthropic API key
    *
    * Optional environment variables:
    *   - ANTHROPIC_VERSION: API version (defaults to "2023-06-01")
    *   - ANTHROPIC_BASE_URL: Custom base URL (defaults to official Anthropic endpoint)
    *
    * @return
    *   ClaudeConfig instance
    * @throws IllegalArgumentException
    *   if ANTHROPIC_API_KEY is not set
    */
  def fromEnv: ClaudeConfig = {
    val apiKey =
      sys.env.getOrElse("ANTHROPIC_API_KEY", throw new IllegalArgumentException("ANTHROPIC_API_KEY environment variable is required"))
    val anthropicVersion = sys.env.getOrElse("ANTHROPIC_VERSION", DefaultApiVersion)
    val baseUrl = sys.env.get("ANTHROPIC_BASE_URL").map(Uri.unsafeParse).getOrElse(DefaultBaseUrl)

    ClaudeConfig(
      apiKey = apiKey,
      anthropicVersion = anthropicVersion,
      baseUrl = baseUrl
    )
  }
}
