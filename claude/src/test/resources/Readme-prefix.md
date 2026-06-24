![sttp-ai](https://github.com/softwaremill/sttp-ai/raw/master/banner.png)

[![Ideas, suggestions, problems, questions](https://img.shields.io/badge/Discourse-ask%20question-blue)](https://softwaremill.community/c/open-source)
[![CI](https://github.com/softwaremill/sttp-ai/workflows/CI/badge.svg)](https://github.com/softwaremill/sttp-ai/actions?query=workflow%3ACI+branch%3Amaster)

[![sttp.ai:core](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/core_3/badge.svg?subject=sttp.ai:core)](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/core_3/)
[![sttp.ai:openai](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/openai_3/badge.svg?subject=sttp.ai:openai)](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/openai_3/)
[![sttp.ai:claude](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/claude_3/badge.svg?subject=sttp.ai:claude)](https://maven-badges.sml.io/sonatype-central/com.softwaremill.sttp.ai/claude_3/)

sttp is a family of Scala HTTP-related projects, and currently includes:

* [sttp client](https://github.com/softwaremill/sttp): The Scala HTTP client you always wanted!
* [sttp tapir](https://github.com/softwaremill/tapir): Typed API descRiptions
* sttp ai: this project. Non-official Scala client wrapper for OpenAI, Claude (Anthropic), and OpenAI-compatible APIs. Use the power of ChatGPT and Claude inside your code!

## Table of Contents

- [Intro](#intro)
- [Quickstart](#quickstart)
    - [OpenAI/OpenAI-compatible APIs](#for-openaiopenai-compatible-apis)
    - [Claude (Anthropic) API](#for-claude-anthropic-api)
- [OpenAI API](#openai-api)
    - [Basic Usage](#basic-usage-openai)
    - [Streaming](#streaming-openai)
    - [Structured Outputs/JSON Schema](#structured-outputsjson-schema-support)
    - [Function/Tool Calling](#generating-json-schema-from-case-class)
- [Claude API](#claude-api)
    - [Features](#claude-features)
    - [Basic Usage](#basic-usage-claude)
    - [Configuration](#claude-configuration)
    - [Messages API](#claude-messages-api)
    - [Structured Outputs](#claude-structured-outputs)
    - [Tool Calling](#claude-tool-calling)
    - [Streaming](#claude-streaming)
    - [Models API](#claude-models-api)
    - [Error Handling](#claude-error-handling)
    - [Key Differences from OpenAI](#key-differences-from-openai-api)
    - [Synchronous Claude Client](#synchronous-claude-client)
- [Agent Loop](#agent-loop)
    - [Exception Handling](#exception-handling)
- [OpenAI-Compatible APIs](#openai-compatible-apis)
- [Examples](#examples)
- [Contributing](#contributing)
- [Commercial Support](#commercial-support)
- [Copyright](#copyright)

## Intro

sttp-ai uses sttp client to describe requests and responses used in OpenAI, Claude (Anthropic), and OpenAI-compatible endpoints.

## Quickstart

### For OpenAI/OpenAI-compatible APIs

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "openai" % "0.4.14"
```

### For Claude (Anthropic) API

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "claude" % "0.4.14"

// For streaming support, add one or more:
"com.softwaremill.sttp.ai" %% "claude-streaming-fs2" % "0.4.14"    // cats-effect/fs2
"com.softwaremill.sttp.ai" %% "claude-streaming-zio" % "0.4.14"    // ZIO
"com.softwaremill.sttp.ai" %% "claude-streaming-akka" % "0.4.14"   // Akka Streams (Scala 2.13 only)
"com.softwaremill.sttp.ai" %% "claude-streaming-pekko" % "0.4.14"  // Pekko Streams
"com.softwaremill.sttp.ai" %% "claude-streaming-ox" % "0.4.14"    // Ox direct-style (Scala 3 only)
```

sttp-openai is available for Scala 2.13 and Scala 3

## OpenAI API

OpenAI API Official Documentation: https://platform.openai.com/docs/api-reference/completions

Examples are runnable using [scala-cli](https://scala-cli.virtuslab.org).

### Basic Usage (OpenAI)

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:0.4.14

import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

object Main:
  def main(args: Array[String]): Unit =
    val apiKey = System.getenv("OPENAI_KEY")
    val openAI = OpenAISyncClient(apiKey)

    // Create body of Chat Completions Request
    val bodyMessages: Seq[Message] = Seq(
      Message.UserMessage(
        content = Content.TextContent("Hello!"),
      )
    )

    // use ChatCompletionModel.CustomChatCompletionModel("gpt-some-future-version")
    // for models not yet supported here
    val chatRequestBody: ChatBody = ChatBody(
      model = ChatCompletionModel.GPT4oMini,
      messages = bodyMessages
    )

    // be aware that calling `createChatCompletion` may throw an OpenAIException
    // e.g. AuthenticationException, RateLimitException and many more
    val chatResponse: ChatResponse = openAI.createChatCompletion(chatRequestBody)

    println(chatResponse)
    /*
        ChatResponse(
         chatcmpl-79shQITCiqTHFlI9tgElqcbMTJCLZ,chat.completion,
         1682589572,
         gpt-4o-mini,
         Usage(10,10,20),
         List(
           Choices(
             Message(assistant, Hello there! How can I assist you today?), stop, 0)
           )
         )
    */
```

## Claude API

This module provides **native support for Anthropic's Claude API** within the sttp-openai library. Unlike OpenAI compatibility layers, this provides direct access to Claude's unique features and API structure.

### Claude Features

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

### Basic Usage (Claude)

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::claude:0.4.14

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
          case ContentBlock.TextContent(text, _) => println(text)
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

### Claude Configuration

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

### Claude Messages API

#### Basic Text Conversation

```scala
val messages = List(
  Message.user(List(ContentBlock.text("What is the capital of France?"))),
  Message.assistant(List(ContentBlock.text("The capital of France is Paris."))),
  Message.user(List(ContentBlock.text("What about Italy?")))
)

val request = MessageRequest.simple(
  model = "claude-3-sonnet-20240229",
  messages = messages,
  maxTokens = 1000
)
```

#### System Messages

Unlike OpenAI, Claude uses a separate `system` parameter instead of system role messages:

```scala
val request = MessageRequest.withSystem(
  model = "claude-3-sonnet-20240229",
  system = "You are a helpful assistant that always responds in French.",
  messages = List(Message.user(List(ContentBlock.text("Hello!")))),
  maxTokens = 1000
)
```

#### Image Support

```scala
import java.util.Base64
import java.nio.file.{Files, Paths}

// Read and encode image
val imageBytes = Files.readAllBytes(Paths.get("image.jpg"))
val base64Image = Base64.getEncoder.encodeToString(imageBytes)

val messages = List(
  Message.user(List(
    ContentBlock.text("What do you see in this image?"),
    ContentBlock.image(
      mediaType = "image/jpeg",
      data = base64Image
    )
  ))
)

val request = MessageRequest.simple(
  model = "claude-3-sonnet-20240229",
  messages = messages,
  maxTokens = 1000
)
```

#### Advanced Parameters

```scala
val request = MessageRequest(
  model = "claude-3-sonnet-20240229",
  messages = messages,
  maxTokens = 4000,
  temperature = Some(0.7),           // Creativity (0.0 - 1.0)
  topP = Some(0.9),                  // Nucleus sampling
  topK = Some(40),                   // Top-k sampling
  stopSequences = Some(List("\n\n")), // Stop generation at sequences
  system = Some("Be concise and helpful."),
  tools = Some(tools)                // Tool calling support
)
```

### Claude Structured Outputs

Claude's structured output feature (currently in beta) allows you to enforce that the model's response follows a specific JSON schema. This is useful for getting consistently formatted responses for data extraction, API responses, and structured data processing.

**Model Support:**
- ✅ **Supported models**: Claude 4.1+ models (`claude-sonnet-4-1-20250514`, `claude-opus-4-1-20250514`, etc.)
- ❌ **Legacy models**: Claude 3.x series don't support structured outputs
- ✅ **Forward compatibility**: Unknown/future models default to supported

#### Typed responses with `createMessageAs[T]`

For the shortest path, use `ClaudeSyncClient.createMessageAs[T]` — the response schema is derived from `T` via Tapir, set on the request automatically, and the model's response is parsed back into `T` via uPickle.

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::claude:0.4.14

import sttp.ai.claude.ClaudeSyncClient
import sttp.ai.claude.models.Message
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.json.SnakePickle
import sttp.tapir.Schema

case class Language(name: String, paradigm: String, summary: String) derives SnakePickle.ReadWriter, Schema
case class LanguageList(languages: List[Language]) derives SnakePickle.ReadWriter, Schema

object Main:
  def main(args: Array[String]): Unit =
    val claude = ClaudeSyncClient.fromEnv
    try {
      val request = MessageRequest.simple(
        model = "claude-haiku-4-5-20251001",
        messages = List(Message.user(
          "List 10 well-known programming languages. For each, give the dominant paradigm and a one-sentence summary."
        )),
        maxTokens = 1500
      )
      val result: LanguageList = claude.createMessageAs[LanguageList](request)
      result.languages.foreach(l => println(s"${l.name} [${l.paradigm}] — ${l.summary}"))
    } finally claude.close()
```

`T` must have both a `sttp.tapir.Schema[T]` (for schema generation) and a `SnakePickle.ReadWriter[T]` (for parsing) — the `derives` clause supplies both in Scala 3.

#### Basic Structured Output Example

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::claude:0.4.14
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.7

import sttp.ai.claude.*
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, Message, OutputFormat}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.json.SnakePickle
import sttp.apispec.{Schema => ASchema}
import sttp.client4.*
import sttp.tapir.Schema
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.generic.auto.*

object StructuredOutputExample:
  // Define case class with Schema derivation
  case class PersonInfo(
    name: String,
    age: Int,
    occupation: String,
    skills: List[String]
  )

  object PersonInfo:
    given SnakePickle.Reader[PersonInfo] = SnakePickle.macroR[PersonInfo]

  def main(args: Array[String]): Unit =
    val config = ClaudeConfig.fromEnv
    val backend: SyncBackend = DefaultSyncBackend()
    val client = ClaudeClient(config)

    // Generate JSON schema from case class using Tapir
    val tapirSchema = implicitly[Schema[PersonInfo]]
    val jsonSchema: ASchema = TapirSchemaToJsonSchema(tapirSchema, markOptionsAsNullable = true)

    val outputFormat = OutputFormat.JsonSchema(jsonSchema)

    val messages = List(
      Message.user(List(ContentBlock.text(
        "Extract information about John, a 30-year-old software engineer who knows Python and Scala."
      )))
    )

    val request = MessageRequest
      .simple("claude-sonnet-4-5-20250514", messages, 500)
      .withStructuredOutput(outputFormat)

    val response = client.createMessage(request).send(backend)

    response.body match {
      case Right(messageResponse) =>
        messageResponse.content.foreach {
          case ContentBlock.TextContent(text, _) =>
            println("Structured JSON output:")
            println(text)

            // Parse JSON response back to case class
            val personInfo = SnakePickle.read[PersonInfo](text)
            println(s"Parsed: ${personInfo.name}, age ${personInfo.age}, ${personInfo.occupation}")
            println(s"Skills: ${personInfo.skills.mkString(", ")}")
          case _ => // Handle other content types
        }
      case Left(error) =>
        println(s"Error: ${error.getMessage}")
    }

    backend.close()
```

#### Manual Schema Definition

If you prefer not to use Tapir, you can define schemas manually:

```scala
import sttp.apispec.{Schema => ASchema, SchemaType}
import scala.collection.immutable.ListMap

val schema: ASchema = ASchema(SchemaType.Object).copy(
  properties = ListMap(
    "summary" -> ASchema(SchemaType.String),
    "confidence" -> ASchema(SchemaType.Number).copy(minimum = Some(0), maximum = Some(1))
  ),
  required = List("summary", "confidence")
)
val outputFormat = OutputFormat.JsonSchema(schema)
```

**Important Notes:**
- Structured outputs require Claude 4.1+ models (`claude-sonnet-4-1-*`, `claude-opus-4-1-*`, etc.)
- Legacy models will throw `UnsupportedModelForStructuredOutputException`
- The beta feature uses `anthropic-beta: structured-outputs-2025-11-13` header automatically
- Unknown/future models default to supporting structured outputs for forward compatibility
- JSON schemas must be valid and follow standard JSON Schema specifications

### Claude Tool Calling

#### Custom Tools

Define your own tools that Claude calls and your application executes:

```scala
import sttp.ai.claude.models.{Tool, ToolInputSchema, PropertySchema}

val weatherTool = Tool(
  name = "get_weather",
  description = "Get current weather for a location",
  inputSchema = ToolInputSchema(
    `type` = "object",
    properties = Map(
      "location" -> PropertySchema(`type` = "string", description = Some("City name")),
      "unit" -> PropertySchema(`type` = "string", `enum` = Some(List("celsius", "fahrenheit")))
    ),
    required = Some(List("location"))
  )
)

val request = MessageRequest.withTools(
  model = "claude-3-sonnet-20240229",
  messages = List(Message.user(List(ContentBlock.text("What's the weather in Paris?")))),
  maxTokens = 1000,
  tools = List(weatherTool)
)
```

#### Predefined Tools

Currently supported:

- **`Tool.WebSearch`** (`web_search_20250305`)

```scala
import sttp.ai.claude.models.{ContentBlock, Message, Tool}
import sttp.ai.claude.requests.MessageRequest

val request = MessageRequest.withTools(
  model = "claude-sonnet-4-5-20250514",
  messages = List(Message.user(List(ContentBlock.text("What was the most recent SpaceX launch?")))),
  maxTokens = 1024,
  tools = List(Tool.WebSearch.default)
)

val response = client.createMessage(request)

response.content.foreach {
  case t: ContentBlock.TextContent              => println(t.text)
  case s: ContentBlock.ServerToolUseContent     =>
    println(s"Searched for: ${s.input.get("query").map(_.str).getOrElse("")}")
  case r: ContentBlock.WebSearchToolResultContent =>
    r.content match {
      case ContentBlock.WebSearchToolResult.Results(items) =>
        items.foreach(it => println(s"- ${it.title} — ${it.url}"))
      case ContentBlock.WebSearchToolResult.Error(code) =>
        println(s"Web search failed: $code")
    }
  case _                                        => ()
}
```

Both custom and predefined tools can be passed in the same `tools` list.

### Claude Streaming

#### Using fs2 (cats-effect)

```scala
import sttp.ai.claude.streaming.fs2.*
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import cats.effect.IO

val backend = HttpClientFs2Backend[IO]()

// Extension method for streaming
val streamRequest = client.createMessageAsBinaryStream(backend.capabilities.streams, request)

streamRequest
  .send(backend)
  .map(_.map(_.parseSSE.parseClaudeStreamResponse))
  .flatMap {
    case Right(stream) =>
      stream
        .evalTap(response => IO.println(response.delta.text.getOrElse("")))
        .compile
        .drain
    case Left(error) =>
      IO.println(s"Error: $error")
  }
```

#### Using ZIO

```scala
import sttp.ai.claude.streaming.zio.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import zio.*

val backend = HttpClientZioBackend()

val program = for {
  streamRequest <- ZIO.succeed(client.createMessageAsBinaryStream(backend.capabilities.streams, request))
  result <- streamRequest.send(backend)
  _ <- result match {
    case Right(stream) =>
      stream
        .parseSSE
        .parseClaudeStreamResponse
        .tap(response => Console.printLine(response.delta.text.getOrElse("")))
        .runDrain
    case Left(error) =>
      Console.printLine(s"Error: $error")
  }
} yield ()
```

#### Using Ox (Scala 3)

```scala
import sttp.ai.claude.streaming.ox.*
import sttp.client4.ox.OxHttpClientBackend
import ox.*

val backend = OxHttpClientBackend()

val streamRequest = client.createMessageAsBinaryStream(backend.capabilities.streams, request)

val result = streamRequest.send(backend)
result match {
  case Right(stream) =>
    stream
      .parseSSE
      .parseClaudeStreamResponse
      .tap(response => println(response.delta.text.getOrElse("")))
      .runDrain()
  case Left(error) =>
    println(s"Error: $error")
}
```

### Claude Models API

```scala
val modelsRequest = client.listModels()
val models = modelsRequest.send(backend)

models match {
  case Right(response) =>
    response.data.foreach(model => println(s"${model.id} - ${model.displayName.getOrElse("N/A")}"))
  case Left(error) =>
    println(s"Error: $error")
}
```

**Common Claude models** (use `listModels()` for current list):

- `claude-3-sonnet-20240229` - Balanced performance and speed
- `claude-3-opus-20240229` - Highest capability model
- `claude-3-haiku-20240307` - Fastest model
- `claude-instant-1.2` - Legacy fast model

### Claude Error Handling

Claude-specific exception hierarchy:

```scala
import sttp.ai.claude.ClaudeExceptions.*

client.createMessage(request).send(backend) match {
  case Right(response) => // Success
    handleResponse(response)
  case Left(error) => error match {
    case _: AuthenticationException => // Invalid API key
      println("Authentication failed - check your API key")
    case _: RateLimitException => // Rate limited
      println("Rate limited - please wait before retrying")
    case _: InvalidRequestException => // Malformed request
      println("Invalid request - check your parameters")
    case _: PermissionException => // Access denied
      println("Permission denied for this resource")
    case _: APIException => // Other API error
      println(s"API error: ${error.getMessage}")
    case _: DeserializationClaudeException => // JSON parsing error
      println("Failed to parse response")
  }
}
```

### Key Differences from OpenAI API

| Feature | Claude API | OpenAI API |
|---------|------------|------------|
| **Message Content** | `ContentBlock` arrays | Simple strings |
| **System Messages** | `system` parameter | Role-based message |
| **Authentication** | `x-api-key` + `anthropic-version` headers | `Authorization` header |
| **Image Input** | ContentBlock with base64 | URL or base64 in content |
| **Tool Calling** | Native tool structure | Function calling |
| **Streaming** | Server-Sent Events | Server-Sent Events |
| **Model Names** | `claude-3-sonnet-20240229` | `gpt-4` |

### Synchronous Claude Client

For blocking operations, use `ClaudeSyncClient`:

```scala
import sttp.ai.claude.ClaudeSyncClient

val syncClient = new ClaudeSyncClient(config)

// Throws ClaudeException on error
try {
  val response = syncClient.createMessage(request)
  println(response.content.head.text.getOrElse(""))
} catch {
  case e: ClaudeException => println(s"Error: ${e.getMessage}")
}
```