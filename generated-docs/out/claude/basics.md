# Claude API

This module provides **native support for Anthropic's Claude API** within the sttp-openai library. Unlike OpenAI compatibility layers, this provides direct access to Claude's unique features and API structure.

## Claude Features

- ✅ **Native Claude API support** - Direct Claude API integration, not compatibility layer
- ✅ **ContentBlock structure** - Support for Claude's rich message content blocks (text, images)
- ✅ **Proper Authentication** - Uses `x-api-key` and `anthropic-version` headers
- ✅ **Messages API** - Complete `/v1/messages` endpoint implementation
- ✅ **Models API** - List available Claude models via `/v1/models`
- ✅ **Streaming Support** - Server-Sent Events streaming for all effect systems (fs2, ZIO, Akka, Pekko, Ox)
- ✅ **Tool Calling** - Native Claude tool calling support
- ✅ **Structured Outputs** - Beta support for JSON schema validation (Claude 4.1+ models)
- ✅ **Image Support** - Multi-modal inputs via ContentBlock with base64 encoding
- ✅ **Comprehensive Error Handling** - Claude-specific exception hierarchy
- ✅ **System Messages** - Proper system message handling via `system` parameter
- ✅ **Cross-platform** - Support for Scala 2.13 and Scala 3

## Basic Usage (Claude)

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.5.1+17-73bc7345+20260714-1005-SNAPSHOT

import sttp.ai.claude.*
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, Message}
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
      model = "claude-3-haiku-20240307",  // Fast, cost-effective model
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

**Key differences from OpenAI:**
- Uses `ContentBlock` instead of simple strings for rich content (text, images)
- Separate system parameter instead of system role messages
- Different authentication headers (`x-api-key` + `anthropic-version`)
- Native Claude model names (e.g., `claude-3-haiku-20240307`)

## Claude Configuration

```scala
case class ClaudeConfig(
  apiKey: String,                                    // Your Anthropic API key
  anthropicVersion: String = "2023-06-01",          // API version header
  baseUrl: Uri = "https://api.anthropic.com",       // API base URL
  timeout: Duration = 60.seconds,                   // Request timeout
  maxRetries: Int = 3,                             // Max retry attempts
  organization: Option[String] = None               // Optional organization ID
)
```

**Environment Variables:**
- `ANTHROPIC_API_KEY` - Your API key (required)
- `ANTHROPIC_VERSION` - API version (optional, defaults to "2023-06-01")
- `ANTHROPIC_BASE_URL` - Custom base URL (optional)

