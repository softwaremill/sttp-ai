package sttp.ai.openai

/** Controls how the API key is attached to outgoing requests.
  *
  *   - [[AuthScheme.Bearer]] sends `Authorization: Bearer <key>` — OpenAI and most compatible providers (default).
  *   - [[AuthScheme.AzureApiKey]] sends `api-key: <key>` — Azure OpenAI deployment endpoints.
  */
sealed trait AuthScheme

object AuthScheme {
  case object Bearer extends AuthScheme
  case object AzureApiKey extends AuthScheme
}
