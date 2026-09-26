package sttp.ai.jev

import sttp.ai.core.config.AIClientConfig
import sttp.model.Uri

import scala.concurrent.duration.Duration

/** Configuration of the Jev (TypeSafe AI) clients.
  *
  * @param apiKey
  *   TypeSafe API key, sent as a bearer token
  * @param baseUrl
  *   Base URL of the API; the `v1` path is appended by the client
  * @param model
  *   Model asked by every request (defaults to `jev-latest`)
  * @param timeout
  *   Request timeout, applied to every request
  * @param maxRetries
  *   Retries of transient failures, honored by [[JevSyncClient]]
  * @param organization
  *   Unused by Jev, present for [[AIClientConfig]] parity
  */
final case class JevConfig(
    apiKey: String,
    baseUrl: Uri = JevConfig.DefaultBaseUrl,
    // lives here rather than on `ask`: `ask` is overloaded (single question, tuple) and Scala forbids default arguments on overloaded methods
    model: JevModel = JevModel.JevLatest,
    timeout: Duration = AIClientConfig.DefaultTimeout,
    maxRetries: Int = AIClientConfig.DefaultMaxRetries,
    organization: Option[String] = None
) extends AIClientConfig:
  override def authHeaders: Map[String, String] = Map(
    "Authorization" -> s"Bearer $apiKey",
    "content-type" -> "application/json"
  )

object JevConfig:
  val DefaultBaseUrl: Uri = Uri.unsafeParse("https://api.typesafe.ai")

  /** Reads the configuration from the environment variables of the official SDKs: `TYPESAFE_API_KEY` (required; `JEV_API_KEY` is accepted
    * as a fallback), `TYPESAFE_BASE_URL` and `TYPESAFE_DEFAULT_MODEL` (optional). Empty values count as unset. Throws
    * [[IllegalArgumentException]] when no key is set.
    */
  def fromEnv: JevConfig = fromEnv(sys.env)

  private[jev] def fromEnv(env: Map[String, String]): JevConfig =
    def variable(name: String): Option[String] = env.get(name).filter(_.nonEmpty)
    val apiKey = variable("TYPESAFE_API_KEY")
      .orElse(variable("JEV_API_KEY"))
      .getOrElse(throw new IllegalArgumentException("TYPESAFE_API_KEY (or JEV_API_KEY) environment variable is required"))
    JevConfig(
      apiKey = apiKey,
      baseUrl = variable("TYPESAFE_BASE_URL").map(Uri.unsafeParse).getOrElse(DefaultBaseUrl),
      model = variable("TYPESAFE_DEFAULT_MODEL").map(JevModel.fromString).getOrElse(JevModel.JevLatest)
    )
