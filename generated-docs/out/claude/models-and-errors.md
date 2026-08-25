# Models and error handling

For choosing between the sync and async client, see [basics](basics.md).

## Claude Models API

List the models currently available to your API key:

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.10.0

import sttp.ai.claude.ClaudeSyncClient

object ModelsExample:
  def main(args: Array[String]): Unit =
    val claude = ClaudeSyncClient.fromEnv
    try {
      val models = claude.listModels()
      models.data.foreach(model => println(s"${model.id} - ${model.displayName}"))
    } finally claude.close()
```

Well-known model ids are available as constants on `ClaudeModel` (e.g. `ClaudeModel.ClaudeSonnet5.value`); `ClaudeModel.fromString` parses a raw id back into a catalogued model.

## Claude error handling

`ClaudeSyncClient` throws subclasses of `ClaudeException`; the async `ClaudeClient` returns the same hierarchy in the `Left` branch of `Either[ClaudeException, A]`:

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.10.0

import sttp.ai.claude.ClaudeSyncClient
import sttp.ai.claude.ClaudeExceptions.ClaudeException
import sttp.ai.claude.models.{ClaudeModel, Message}
import sttp.ai.claude.requests.MessageRequest

object ErrorHandlingExample:
  def main(args: Array[String]): Unit =
    val claude = ClaudeSyncClient.fromEnv
    try {
      val request = MessageRequest.simple(ClaudeModel.ClaudeHaiku4_5.value, List(Message.user("Hello!")), 100)

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

Additionally, [structured outputs](structured-outputs.md) on an unsupported model throw `UnsupportedModelForStructuredOutputException` (also under `ClaudeExceptions`). Note that it extends `AIException` directly rather than `ClaudeException`, so a `case e: ClaudeException` catch-all will not catch it.
