package sttp.ai.core.config

import sttp.model.Uri

import scala.concurrent.duration.{Duration, DurationInt}

/** Base trait for AI client configuration.
  *
  * This trait defines the common configuration parameters shared across different AI API clients (OpenAI, Claude, etc.).
  */
trait AIClientConfig {

  /** API key for authentication. */
  def apiKey: String

  /** Base URL for API requests. */
  def baseUrl: Uri

  /** Timeout applied to every request built by the clients (sttp `readTimeout`). Semantics are backend-dependent: on the default Java
    * HttpClient backends this is the time until response headers arrive (so long-lived SSE streams are not cut off mid-stream); some
    * backends (e.g. OkHttp) treat it as the maximum time between bytes. Non-finite values (`Duration.Inf`) disable the timeout.
    */
  def timeout: Duration

  /** Maximum number of retry attempts for transient failures (connection errors, HTTP 408/409/429/5xx), applied on top of the initial
    * attempt; `0` disables retries. Honored by the sync clients (`OpenAISyncClient`, `ClaudeSyncClient`, `GeminiSyncClient`), which wrap
    * their backend in `sttp.ai.core.http.RetryingBackend` — see it for the exact policy. The async clients return raw sttp requests, so
    * this setting cannot apply there automatically; wrap your own backend in `RetryingBackend` with your effect's sleep instead.
    */
  def maxRetries: Int

  /** Optional organization identifier. */
  def organization: Option[String]

  /** Authentication headers to be included in requests.
    *
    * This method should be implemented by concrete config classes to provide API-specific authentication headers.
    */
  def authHeaders: Map[String, String]
}

object AIClientConfig {

  /** Matches the default request timeout of the official OpenAI/Anthropic SDKs. */
  val DefaultTimeout: Duration = 10.minutes

  val DefaultMaxRetries: Int = 3
}
