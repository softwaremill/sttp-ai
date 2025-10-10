package sttp.ai.core.config

import sttp.model.Uri

import scala.concurrent.duration.Duration

/** Base trait for AI client configuration.
  *
  * This trait defines the common configuration parameters shared across different AI API clients (OpenAI, Claude, etc.).
  */
trait AIClientConfig {

  /** API key for authentication. */
  def apiKey: String

  /** Base URL for API requests. */
  def baseUrl: Uri

  /** Request timeout duration. */
  def timeout: Duration

  /** Maximum number of retry attempts for failed requests. */
  def maxRetries: Int

  /** Optional organization identifier. */
  def organization: Option[String]

  /** Authentication headers to be included in requests.
    *
    * This method should be implemented by concrete config classes to provide API-specific authentication headers.
    */
  def authHeaders: Map[String, String]
}
