# Claude API basics

This module provides **native support for Anthropic's [Claude API](https://docs.anthropic.com/claude/reference)** within the sttp-ai library. Unlike OpenAI compatibility layers, this provides direct access to Claude's unique features and API structure.

## Claude features

- ✅ **Messages API** — complete `/v1/messages` implementation; see [Messages API](messages.md)
- ✅ **ContentBlock structure** — rich message content blocks (text, images); see [Messages API](messages.md)
- ✅ **Streaming** — [server-sent events streaming](streaming.md) for fs2, ZIO, Akka, Pekko, and Ox
- ✅ **Tool calling** — [native Claude tools](tool-calling.md), custom and predefined
- ✅ **Structured outputs** — [beta JSON-schema validation](structured-outputs.md) (Claude 4.1+ models)
- ✅ **Models API** — [list available models](models-and-errors.md) via `/v1/models`
- ✅ **Error handling** — [Claude-specific exception hierarchy](models-and-errors.md)
- ✅ **Agent loop** — [autonomous tool-calling agents](../agents/quickstart.md) via `ClaudeAgent`
- ✅ **Proper authentication** — `x-api-key` and `anthropic-version` headers, handled automatically
- ✅ **Cross-platform** — Scala 2.13 and Scala 3

## Sync and async clients

- `ClaudeSyncClient` — high-level and blocking: methods return the response directly and throw a `ClaudeException` subclass on error. The recommended default, used in most examples in these docs. Create it with `ClaudeSyncClient.fromEnv` (reads `ANTHROPIC_API_KEY`) or `ClaudeSyncClient(config)`; call `close()` when done.
- `ClaudeClient` — returns raw sttp-client4 `Request`s and parses responses as `Either[ClaudeException, A]`. Pair it with the sttp backend of your choice (cats-effect, ZIO, Akka/Pekko, Ox).

## Basic usage

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.10.0

import sttp.ai.claude.ClaudeSyncClient
import sttp.ai.claude.models.{ClaudeModel, ContentBlock, Message}
import sttp.ai.claude.requests.MessageRequest

object Main:
  def main(args: Array[String]): Unit =
    val claude = ClaudeSyncClient.fromEnv // reads ANTHROPIC_API_KEY
    try {
      val request = MessageRequest.simple(
        model = ClaudeModel.ClaudeHaiku4_5.value,
        messages = List(Message.user("Hello Claude! What's the weather like today?")),
        maxTokens = 500
      )

      // Throws a ClaudeException subclass on error
      val response = claude.createMessage(request)

      response.content.foreach {
        case ContentBlock.Text(text, _, _) => println(text)
        case _                             => () // other content block types
      }
      println(s"Usage: ${response.usage}")
    } finally claude.close()
```

## Async usage

For non-blocking code, use `ClaudeClient` with an sttp backend of your choice:

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.10.0

import sttp.ai.claude.*
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ClaudeModel, ContentBlock, Message}
import sttp.ai.claude.requests.MessageRequest
import sttp.client4.*

object Main:
  def main(args: Array[String]): Unit =
    // Create an instance of ClaudeClient using your Anthropic API key
    // Set ANTHROPIC_API_KEY environment variable or pass it directly
    val config = ClaudeConfig.fromEnv  // reads ANTHROPIC_API_KEY
    val backend: SyncBackend = DefaultSyncBackend()
    val client = ClaudeClient(config)

    // Create a simple message
    val messages = List(
      Message.user(List(ContentBlock.text("Hello Claude! What's the weather like today?")))
    )

    val request = MessageRequest.simple(
      model = ClaudeModel.ClaudeHaiku4_5.value,  // Fast, cost-effective model
      messages = messages,
      maxTokens = 500
    )

    // Send the request (returns Either[ClaudeException, MessageResponse])
    val response = client.createMessage(request).send(backend)

    response.body match {
      case Right(messageResponse) =>
        messageResponse.content.foreach {
          case ContentBlock.Text(text, _, _) => println(text)
          case _ => // Handle other content types if needed
        }
        println(s"Usage: ${messageResponse.usage}")
      case Left(error) =>
        println(s"Claude API Error: ${error.getMessage}")
    }

    backend.close()
```

The example above uses the synchronous sttp backend for brevity, but `ClaudeClient` works with any sttp4 backend — cats-effect, ZIO, Akka/Pekko, Ox, etc. — by swapping the backend and calling `.send(backend)` in the corresponding effect.

**Key differences from OpenAI:**
- Uses `ContentBlock` instead of simple strings for rich content (text, images)
- Separate system parameter instead of system role messages
- Different authentication headers (`x-api-key` + `anthropic-version`)
- Native Claude model names (e.g., `claude-haiku-4-5-20251001`)

## Claude configuration

```scala
case class ClaudeConfig(
  apiKey: String,                                    // Your Anthropic API key
  anthropicVersion: String = "2023-06-01",          // API version header
  baseUrl: Uri = "https://api.anthropic.com",       // API base URL
  timeout: Duration = 10.minutes,                   // Request timeout
  maxRetries: Int = 3,                             // Max retry attempts, honored by ClaudeSyncClient
  organization: Option[String] = None               // Optional organization ID
)
```

**Environment Variables:**
- `ANTHROPIC_API_KEY` - Your API key (required)
- `ANTHROPIC_VERSION` - API version (optional, defaults to "2023-06-01")
- `ANTHROPIC_BASE_URL` - Custom base URL (optional)

**Next steps:** see [Messages API](messages.md) for conversations, images, and advanced parameters, [Tool calling](tool-calling.md) and [Structured outputs](structured-outputs.md) for advanced request features, [Streaming](streaming.md) for SSE support, and [Models and error handling](models-and-errors.md) for the models API and exception hierarchy.
