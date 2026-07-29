# Models and error handling

For choosing between the sync and async client, see [basics](basics.md).

## Claude Models API

List the models currently available to your API key:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::claude:@VERSION@

import sttp.ai.claude.ClaudeSyncClient

object ModelsExample:
  def main(args: Array[String]): Unit =
    val claude = ClaudeSyncClient.fromEnv
    try {
      val models = claude.listModels()
      models.data.foreach(model => println(s"${model.id} - ${model.displayName}"))
    } finally claude.close()
```

## Claude error handling

`ClaudeSyncClient` throws subclasses of `ClaudeException`; the async `ClaudeClient` returns the same hierarchy in the `Left` branch of `Either[ClaudeException, A]`:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::claude:@VERSION@

import sttp.ai.claude.ClaudeSyncClient
import sttp.ai.claude.ClaudeExceptions.ClaudeException
import sttp.ai.claude.models.Message
import sttp.ai.claude.requests.MessageRequest

object ErrorHandlingExample:
  def main(args: Array[String]): Unit =
    val claude = ClaudeSyncClient.fromEnv
    try {
      val request = MessageRequest.simple("claude-haiku-4-5-20251001", List(Message.user("Hello!")), 100)

      try {
        val response = claude.createMessage(request)
        println(response.content)
      } catch {
        case _: ClaudeException.AuthenticationException        => println("Authentication failed - check your API key")
        case _: ClaudeException.RateLimitException             => println("Rate limited - please wait before retrying")
        case _: ClaudeException.InvalidRequestException        => println("Invalid request - check your parameters")
        case _: ClaudeException.PermissionException            => println("Permission denied for this resource")
        case _: ClaudeException.TryAgain                       => println("Transient error - retry the request")
        case _: ClaudeException.ServiceUnavailableException    => println("Claude is temporarily unavailable")
        case e: ClaudeException.APIException                   => println(s"API error: ${e.getMessage}")
        case _: ClaudeException.DeserializationClaudeException => println("Failed to parse response")
      }
    } finally claude.close()
```

Additionally, [structured outputs](structured-outputs.md) on an unsupported model throw `UnsupportedModelForStructuredOutputException` (also under `ClaudeExceptions`).
