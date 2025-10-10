package sttp.ai.openai.config

import sttp.ai.core.config.AIClientConfig
import sttp.model.Uri

import scala.concurrent.duration.{Duration, DurationInt}

/** Configuration for OpenAI API client.
  *
  * @param apiKey
  *   OpenAI API key for authentication
  * @param baseUrl
  *   Base URL for OpenAI API (defaults to official OpenAI endpoint)
  * @param timeout
  *   Request timeout duration (defaults to 60 seconds)
  * @param maxRetries
  *   Maximum number of retry attempts (defaults to 3)
  * @param organization
  *   Optional organization identifier for OpenAI API
  */
case class OpenAIConfig(
    apiKey: String,
    baseUrl: Uri = OpenAIConfig.DefaultBaseUrl,
    timeout: Duration = 60.seconds,
    maxRetries: Int = 3,
    organization: Option[String] = None
) extends AIClientConfig {

  override def authHeaders: Map[String, String] = {
    val baseHeaders = Map(
      "Authorization" -> s"Bearer $apiKey",
      "content-type" -> "application/json"
    )
    organization match {
      case Some(org) => baseHeaders + ("OpenAI-Organization" -> org)
      case None      => baseHeaders
    }
  }
}

object OpenAIConfig {
  val DefaultBaseUrl: Uri = Uri.unsafeParse("https://api.openai.com/v1")

  /** Creates OpenAIConfig from environment variables.
    *
    * Required environment variables:
    *   - OPENAI_API_KEY: OpenAI API key
    *
    * Optional environment variables:
    *   - OPENAI_BASE_URL: Custom base URL (defaults to official OpenAI endpoint)
    *   - OPENAI_ORGANIZATION: Organization identifier
    *
    * @return
    *   OpenAIConfig instance
    * @throws IllegalArgumentException
    *   if OPENAI_API_KEY is not set
    */
  def fromEnv: OpenAIConfig = {
    val apiKey = sys.env.getOrElse("OPENAI_API_KEY", throw new IllegalArgumentException("OPENAI_API_KEY environment variable is required"))
    val baseUrl = sys.env.get("OPENAI_BASE_URL").map(Uri.unsafeParse).getOrElse(DefaultBaseUrl)
    val organization = sys.env.get("OPENAI_ORGANIZATION")

    OpenAIConfig(
      apiKey = apiKey,
      baseUrl = baseUrl,
      organization = organization
    )
  }
}
