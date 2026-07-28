# Models and errors

## Gemini Models

`GeminiModel` is a small catalogue of well-known model identifiers. It is optional — every request-building method (`InteractionRequest.simple`, `.withSystem`, `.withTools`, or the `InteractionRequest` constructor) takes a plain `model: String`, so you can always pass a literal model name directly. Use `GeminiModel.value` when you'd rather reference a model symbolically, and `GeminiModel.CustomModel` for anything not listed:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::gemini:@VERSION@

import sttp.ai.gemini.models.GeminiModel

object ModelsExample:
  def main(args: Array[String]): Unit =
    println(GeminiModel.Gemini25Flash.value) // "gemini-2.5-flash"
    GeminiModel.values.foreach(m => println(m.value))

    val custom = GeminiModel.CustomModel("gemini-future-model")
    println(custom.value)
```

**Known models** (`GeminiModel.values`):

- `Gemini36Flash` (`gemini-3.6-flash`)
- `Gemini35Flash` (`gemini-3.5-flash`)
- `Gemini35FlashLite` (`gemini-3.5-flash-lite`)
- `Gemini31FlashLite` (`gemini-3.1-flash-lite`)
- `Gemini25Pro` (`gemini-2.5-pro`) — highest-capability, best for complex reasoning
- `Gemini25Flash` (`gemini-2.5-flash`) — balanced performance and speed
- `Gemini25FlashLite` (`gemini-2.5-flash-lite`) — fastest and cheapest, good for integration tests

`GeminiModel.fromString(s)` parses a raw model name back into a known `GeminiModel` case object, falling back to `CustomModel(s)`.

> **Note:** the 2.5-generation `-lite` models (`gemini-2.5-flash-lite` and older) are no longer available to new Gemini API users — requesting them returns a 404 `not_found` error. Prefer `Gemini35FlashLite` (`gemini-3.5-flash-lite`) or later for new integrations.

## Gemini Error Handling

`GeminiClient`'s async methods return `Either[GeminiException, A]`; `GeminiSyncClient` throws the same exceptions instead. Every non-2xx HTTP response is mapped to a `GeminiException` subclass based on the status code, since the Gemini error body's `code` field is not a stable error `type` — it can be either a JSON string (e.g. `"not_found"`) or a JSON number depending on the endpoint, and a Google status string (e.g. `RESOURCE_EXHAUSTED`) is not always present either:

| HTTP Status | Exception |
|-------------|-----------|
| 401 Unauthorized | `GeminiException.AuthenticationException` |
| 403 Forbidden | `GeminiException.PermissionException` |
| 404 Not Found | `GeminiException.NotFoundException` |
| 400 Bad Request | `GeminiException.InvalidRequestException` |
| 429 Too Many Requests | `GeminiException.RateLimitException` |
| 503 Service Unavailable | `GeminiException.ServiceUnavailableException` |
| Any other non-2xx status | `GeminiException.APIException` |
| (response body fails to parse as `T`) | `GeminiException.DeserializationGeminiException` |

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::gemini:@VERSION@

import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.requests.InteractionRequest

object ErrorHandlingExample:
  def main(args: Array[String]): Unit =
    val gemini = GeminiSyncClient.fromEnv
    try {
      val request = InteractionRequest.simple("gemini-2.5-flash", "Hello!")

      try {
        val response = gemini.createInteraction(request)
        println(response.outputText)
      } catch {
        case _: GeminiException.AuthenticationException      => println("Authentication failed - check your API key")
        case _: GeminiException.RateLimitException            => println("Rate limited - please wait before retrying")
        case _: GeminiException.InvalidRequestException        => println("Invalid request - check your parameters")
        case _: GeminiException.PermissionException            => println("Permission denied for this resource")
        case _: GeminiException.NotFoundException              => println("Interaction not found")
        case _: GeminiException.ServiceUnavailableException    => println("Gemini is temporarily unavailable")
        case e: GeminiException.APIException                   => println(s"API error: ${e.getMessage}")
        case _: GeminiException.DeserializationGeminiException => println("Failed to parse response")
      }
    } finally gemini.close()
```

On the async `GeminiClient`, the same hierarchy shows up in the `Left` branch of `Either[GeminiException, InteractionResponse]` instead of being thrown — pattern-match there the same way.

## Key Differences from OpenAI and Claude

| Feature | Gemini API | Claude API | OpenAI API |
|---------|------------|------------|------------|
| **Endpoint shape** | Single `interactions` resource (create/get/cancel/delete) | `/v1/messages` | `/v1/chat/completions` |
| **Conversation history** | `previousInteractionId` (server-side) or `InteractionInput.StepsInput` (stateless replay) | Resent message list | Resent message list |
| **Message Content** | `Step` + `Content` (rich, structured) | `ContentBlock` arrays | Simple strings |
| **System Instructions** | `systemInstruction` parameter | `system` parameter | Role-based message |
| **Authentication** | `x-goog-api-key` header | `x-api-key` + `anthropic-version` headers | `Authorization` header |
| **Tool Calling** | `Tool.Function` + built-in `GoogleSearch`/`CodeExecution` | Native tool structure | Function calling |
| **Streaming** | Server-Sent Events | Server-Sent Events | Server-Sent Events |
| **Model Names** | `gemini-2.5-flash` | `claude-3-sonnet-20240229` | `gpt-4` |

## Synchronous Gemini Client

For blocking operations, use `GeminiSyncClient`:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::gemini:@VERSION@

import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.requests.InteractionRequest

object SyncClientExample:
  def main(args: Array[String]): Unit =
    val syncClient = GeminiSyncClient(GeminiConfig.fromEnv)

    // Throws a GeminiException subclass on error
    try {
      val response = syncClient.createInteraction(InteractionRequest.simple("gemini-2.5-flash", "Hello!"))
      println(response.outputText)
    } catch {
      case e: GeminiException => println(s"Error: ${e.getMessage}")
    } finally syncClient.close()
```
