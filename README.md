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
  - [Tool Calling](#claude-tool-calling)
  - [Streaming](#claude-streaming)
  - [Models API](#claude-models-api)
  - [Error Handling](#claude-error-handling)
  - [Key Differences from OpenAI](#key-differences-from-openai-api)
  - [Synchronous Claude Client](#synchronous-claude-client)
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
"com.softwaremill.sttp.ai" %% "openai" % "0.4.0"
```

### For Claude (Anthropic) API

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "claude" % "0.4.0"

// For streaming support, add one or more:
"com.softwaremill.sttp.ai" %% "claude-streaming-fs2" % "0.4.0"    // cats-effect/fs2
"com.softwaremill.sttp.ai" %% "claude-streaming-zio" % "0.4.0"    // ZIO
"com.softwaremill.sttp.ai" %% "claude-streaming-akka" % "0.4.0"   // Akka Streams (Scala 2.13 only)
"com.softwaremill.sttp.ai" %% "claude-streaming-pekko" % "0.4.0"  // Pekko Streams
"com.softwaremill.sttp.ai" %% "claude-streaming-ox" % "0.4.0"    // Ox direct-style (Scala 3 only)
```

sttp-openai is available for Scala 2.13 and Scala 3

## OpenAI API

OpenAI API Official Documentation: https://platform.openai.com/docs/api-reference/completions

Examples are runnable using [scala-cli](https://scala-cli.virtuslab.org).

### Basic Usage (OpenAI)

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:0.4.0

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
- ✅ **Image Support** - Multi-modal inputs via ContentBlock with base64 encoding
- ✅ **Comprehensive Error Handling** - Claude-specific exception hierarchy
- ✅ **System Messages** - Proper system message handling via `system` parameter
- ✅ **Cross-platform** - Support for Scala 2.13 and Scala 3

### Basic Usage (Claude)

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::claude:0.4.0

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
      Message.user(List(ContentBlock.TextContent("Hello Claude! What's the weather like today?")))
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
          case ContentBlock.TextContent(text) => println(text)
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

### Claude Tool Calling

```scala
import sttp.ai.claude.models.{Tool, ToolInputSchema, PropertySchema}

val weatherTool = Tool(
  name = "get_weather",
  description = "Get current weather for a location",
  inputSchema = ToolInputSchema(
    `type` = "object",
    properties = Map(
      "location" -> PropertySchema(`type` = "string", description = Some("City name")),
      "unit" -> PropertySchema(`type` = "string", enum = Some(List("celsius", "fahrenheit")))
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

## OpenAI-Compatible APIs

### To use Ollama or Grok (OpenAI-compatible APIs)

Ollama with sync backend:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:0.4.0

import sttp.model.Uri.*
import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

object Main:
  def main(args: Array[String]): Unit =
    // Create an instance of OpenAISyncClient providing any api key
    // and a base url of locally running instance of ollama
    val openAI: OpenAISyncClient = OpenAISyncClient("ollama", uri"http://localhost:11434/v1")

    // Create body of Chat Completions Request
    val bodyMessages: Seq[Message] = Seq(
      Message.UserMessage(
        content = Content.TextContent("Hello!"),
      )
    )

    val chatRequestBody: ChatBody = ChatBody(
      // assuming one has already executed `ollama pull mistral` in console
      model = ChatCompletionModel.CustomChatCompletionModel("mistral"),
      messages = bodyMessages
    )

    // be aware that calling `createChatCompletion` may throw an OpenAIException
    // e.g. AuthenticationException, RateLimitException and many more
    val chatResponse: ChatResponse = openAI.createChatCompletion(chatRequestBody)

    println(chatResponse)
  /*
    ChatResponse(
      chatcmpl-650,
      List(
        Choices(
          Message(Assistant, """Hello there! How can I help you today?""", List(), None),
          "stop",
          0
        )
      ),
      1714663831,
      "mistral",
      "chat.completion",
      Usage(0, 187, 187),
      Some("fp_ollama")
    )
  */
```

Grok with cats-effect based backend:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:0.4.0
//> using dep com.softwaremill.sttp.client4::cats:4.0.0-M17

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.model.Uri.*
import sttp.ai.openai.OpenAI
import sttp.ai.openai.OpenAIExceptions.OpenAIException
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

object Main:
  def main(args: Array[String]): Unit =
    val apiKey = System.getenv("OPENAI_KEY")
    val openAI = new OpenAI(apiKey, uri"https://api.groq.com/openai/v1")

    val bodyMessages: Seq[Message] = Seq(
      Message.UserMessage(
        content = Content.TextContent("Hello!"),
      )
    )

    val chatRequestBody: ChatBody = ChatBody(
      model = ChatCompletionModel.CustomChatCompletionModel("gemma-7b-it"),
      messages = bodyMessages
    )

    val program = HttpClientCatsBackend.resource[IO]().use { backend =>
      val response: IO[Either[OpenAIException, ChatResponse]] =
        openAI
          .createChatCompletion(chatRequestBody)
          .send(backend)
          .map(_.body)
      val rethrownResponse: IO[ChatResponse] = response.rethrow
      val redeemedResponse: IO[String] = rethrownResponse.redeem(
        error => error.getMessage,
        chatResponse => chatResponse.toString
      )
      redeemedResponse.flatMap(IO.println)
    }

    program.unsafeRunSync()
  /*
    ChatResponse(
      "chatcmpl-e0f9f78c-5e74-494c-9599-da02fa495ff8",
      List(
        Choices(
          Message(Assistant, "Hello! 👋 It's great to hear from you. What can I do for you today? 😊", List(), None),
          "stop",
          0
        )
      ),
      1714667435,
      "gemma-7b-it",
      "chat.completion",
      Usage(16, 21, 37),
      Some("fp_f0c35fc854")
    )
  */
```

#### Available client implementations:

* `OpenAISyncClient` which provides high-level methods to interact with OpenAI. All the methods send requests synchronously and are blocking, might throw `OpenAIException`
* `OpenAI` which provides raw sttp-client4 `Request`s and parses `Response`s as `Either[OpenAIException, A]`

If you want to make use of other effects, you have to use `OpenAI` and pass the chosen backend directly to `request.send(backend)` function.

To customize a request when using the `OpenAISyncClient`, e.g. by adding a header, or changing the timeout (via request options), you can use the `.customizeRequest` method on the client.

Example below uses `HttpClientCatsBackend` as a backend, make sure to [add it to the dependencies](https://sttp.softwaremill.com/en/latest/backends/catseffect.html)
or use backend of your choice.

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:0.4.0
//> using dep com.softwaremill.sttp.client4::cats:4.0.0-M17

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.ai.openai.OpenAI
import sttp.ai.openai.OpenAIExceptions.OpenAIException
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

object Main:
  def main(args: Array[String]): Unit =
    val apiKey = System.getenv("OPENAI_KEY")
    val openAI = new OpenAI(apiKey)

    val bodyMessages: Seq[Message] = Seq(
      Message.UserMessage(
        content = Content.TextContent("Hello!"),
      )
    )

    val chatRequestBody: ChatBody = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = bodyMessages
    )

    val program = HttpClientCatsBackend.resource[IO]().use { backend =>
      val response: IO[Either[OpenAIException, ChatResponse]] =
        openAI
          .createChatCompletion(chatRequestBody)
          .send(backend)
          .map(_.body)
      val rethrownResponse: IO[ChatResponse] = response.rethrow
      val redeemedResponse: IO[String] = rethrownResponse.redeem(
        error => error.getMessage,
        chatResponse => chatResponse.toString
      )
      redeemedResponse.flatMap(IO.println)
    }

    program.unsafeRunSync()
  /*
    ChatResponse(
      chatcmpl-79shQITCiqTHFlI9tgElqcbMTJCLZ,chat.completion,
      1682589572,
      gpt-3.5-turbo-0301,
      Usage(10,10,20),
      List(
        Choices(
          Message(assistant, Hello there! How can I assist you today?), stop, 0)
        )
      )
    )
  */
```

### Streaming (OpenAI)

#### Create completion with streaming:

To enable streaming support for the Chat Completion API using server-sent events, you must include the appropriate
dependency for your chosen streaming library. We provide support for the following libraries: _fs2_, _ZIO_, _Akka / Pekko Streams_ and _Ox_.

For example, to use `fs2` add the following dependency & import:

```scala
// sbt dependency
"com.softwaremill.sttp.ai" %% "fs2" % "0.4.0"

// import 
import sttp.ai.openai.streaming.fs2.*
```

Example below uses `HttpClientFs2Backend` as a backend:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::fs2:0.4.0

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.ai.openai.OpenAI
import sttp.ai.openai.streaming.fs2.*
import sttp.ai.openai.OpenAIExceptions.OpenAIException
import sttp.ai.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

object Main:
  def main(args: Array[String]): Unit =
    val apiKey = System.getenv("OPENAI_KEY")
    val openAI = new OpenAI(apiKey)

    val bodyMessages: Seq[Message] = Seq(
      Message.UserMessage(
        content = Content.TextContent("Hello!"),
      )
    )

    val chatRequestBody: ChatBody = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = bodyMessages
    )

    val program = HttpClientFs2Backend.resource[IO]().use { backend =>
      val response: IO[Either[OpenAIException, Stream[IO, ChatChunkResponse]]] =
        openAI
          .createStreamedChatCompletion[IO](chatRequestBody)
          .send(backend)
          .map(_.body)

      response
        .flatMap {
          case Left(exception) => IO.println(exception.getMessage)
          case Right(stream)   => stream.evalTap(IO.println).compile.drain
        }
    }

    program.unsafeRunSync()
  /*
    ...
    ChatChunkResponse(
      "chatcmpl-8HEZFNDmu2AYW8jVvNKyRO4W4KcO8",
      "chat.completion.chunk",
      1699118265,
      "gpt-3.5-turbo-0613",
      List(
        Choices(
          Delta(None, Some("Hi"), None),
          null,
          0
        )
      )
    )
    ...
    ChatChunkResponse(
      "chatcmpl-8HEZFNDmu2AYW8jVvNKyRO4W4KcO8",
      "chat.completion.chunk",
      1699118265,
      "gpt-3.5-turbo-0613",
      List(
        Choices(
          Delta(None, Some(" there"), None),
          null,
          0
        )
      )
    )
    ...
   */
```

To use direct-style streaming (requires Scala 3) add the following dependency & import:

```scala
// sbt dependency
"com.softwaremill.sttp.ai" %% "ox" % "0.4.0"

// import 
import sttp.ai.openai.streaming.ox.*
```

Example code:

```scala
//> using dep com.softwaremill.sttp.ai::ox:0.4.0

import ox.*
import ox.either.orThrow
import sttp.client4.DefaultSyncBackend
import sttp.ai.openai.OpenAI
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*
import sttp.ai.openai.streaming.ox.*

object Main extends OxApp:
  override def run(args: Vector[String])(using Ox): ExitCode =
    val apiKey = System.getenv("OPENAI_KEY")
    val openAI = new OpenAI(apiKey)
    
    val bodyMessages: Seq[Message] = Seq(
      Message.UserMessage(
        content = Content.TextContent("Hello!")
      )
    )
    
    val chatRequestBody: ChatBody = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = bodyMessages
    )
    
    val backend = useCloseableInScope(DefaultSyncBackend())
    openAI
      .createStreamedChatCompletion(chatRequestBody)
      .send(backend)
      .body // this gives us an Either[OpenAIException, Flow[ChatChunkResponse]]
      .orThrow // we choose to throw any exceptions and fail the whole app
      .runForeach(el => println(el.orThrow))
    
    ExitCode.Success
```

See also the [ChatProxy](https://github.com/softwaremill/sttp-openai/blob/master/examples/src/main/scala/examples/ChatProxy.scala) example application.

#### Structured Outputs/JSON Schema support

To take advantage of [OpenAI's Structured Outputs](https://platform.openai.com/docs/guides/structured-outputs/introduction)
and support for JSON Schema, you can use `ResponseFormat.JsonSchema` when creating a completion.

The example below produces a JSON object:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:0.4.0

import scala.collection.immutable.ListMap
import sttp.apispec.{Schema, SchemaType}
import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel, ResponseFormat}
import sttp.ai.openai.requests.completions.chat.message.*

object Main:
  def main(args: Array[String]): Unit =
    val apiKey = System.getenv("OPENAI_KEY")
    val openAI = OpenAISyncClient(apiKey)

    val jsonSchema: Schema =
      Schema(SchemaType.Object).copy(properties =
        ListMap(
          "steps" -> Schema(SchemaType.Array).copy(items =
            Some(Schema(SchemaType.Object).copy(properties =
              ListMap(
                "explanation" -> Schema(SchemaType.String),
                "output" -> Schema(SchemaType.String)
              )
            ))
          ),
          "finalAnswer" -> Schema(SchemaType.String)
        ),
      )

    val responseFormat: ResponseFormat.JsonSchema =
      ResponseFormat.JsonSchema(
        name = "mathReasoning",
        strict = Some(true),
        schema = Some(jsonSchema),
        description = None
      )

    val bodyMessages: Seq[Message] = Seq(
      Message.SystemMessage(content = "You are a helpful math tutor. Guide the user through the solution step by step."),
      Message.UserMessage(content = Content.TextContent("How can I solve 8x + 7 = -23"))
    )

    // Create body of Chat Completions Request, using our JSON Schema as the `responseFormat`
    val chatRequestBody: ChatBody = ChatBody(
      model = ChatCompletionModel.GPT4oMini,
      messages = bodyMessages,
      responseFormat = Some(responseFormat)
    )

    val chatResponse: ChatResponse = openAI.createChatCompletion(chatRequestBody)

    println(chatResponse.choices)
  /*
    List(
      Choices(
        Message(
          Assistant,
          {
            "steps": [
              {"explanation": "Start with the original equation: 8x + 7 = -23", "output": "8x + 7 = -23"},
              {"explanation": "Subtract 7 from both sides to isolate the term with x.", "output": "8x + 7 - 7 = -23 - 7"},
              {"explanation": "This simplifies to: 8x = -30", "output": "8x = -30"},
              {"explanation": "Now, divide both sides by 8 to solve for x.", "output": "x = -30 / 8"},
              {"explanation": "Simplify -30 / 8 to its simplest form. Both the numerator and denominator can be divided by 2.", "output": "x = -15 / 4"}
            ],
            "finalAnswer": "x = -15/4"
          },
          List(),
          None
        ),
        stop,
        0
      )
    )
  */
```

##### Deriving a JSON Schema with tapir

To derive the same math reasoning schema used above, you can use
[Tapir's support for generating a JSON schema from a Tapir schema](https://tapir.softwaremill.com/en/latest/docs/json-schema.html):

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.tapir::tapir-apispec-docs:1.11.7

import sttp.apispec.{Schema => ASchema}
import sttp.tapir.Schema
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.generic.auto.*

case class Step(
  explanation: String,
  output: String
)

case class MathReasoning(
  steps: List[Step],
  finalAnswer: String
)

val tSchema = implicitly[Schema[MathReasoning]]

val jsonSchema: ASchema = TapirSchemaToJsonSchema(
  tSchema,
  markOptionsAsNullable = true
)
```

#### Generating JSON Schema from case class

We can also generate JSON Schema directly from case class, without defining the schema manually.

In the example below I define such use case. User tries to book a flight, using function tool. The flow looks as follows:
- User sends a message with the request to book a flight and provides function tool, which means that there is a function on a client side which 'knows' how to book a flight. Within this call it is necessary to provide Json Schema to define function arguments.
- Assistant sends a message with arguments created based on Json Schema provided in the first step.
- User calls custom function with arguments sent by Assistant before.
- User sends result from the function call to Assistant.
- Assistant sends a final result to User.

The key point here is using `FunctionTool.withSchema[T]` method. With this method, Json Schema can be automatically generated using TapirSchemaToJsonSchema functionality. All we need to do is to define case class with [Tapir Schema](https://tapir.softwaremill.com/en/latest/endpoint/schemas.html) defined for it.

Another helpful feature is adding possibility to create ToolMessage object passing object instead of String, which will be automatically serialized to Json. All you have to do is just define SnakePickle.Writer for specific class.

With all this in mind please remember that it is still required to deserialized arguments, which are sent back by Assistant to call our function.

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:0.4.0

import sttp.ai.openai.OpenAISyncClient
import sttp.ai.core.json.SnakePickle
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatBody
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel.GPT4oMini
import sttp.ai.openai.requests.completions.chat.ToolCall.FunctionToolCall
import sttp.ai.openai.requests.completions.chat.message.Content.TextContent
import sttp.ai.openai.requests.completions.chat.message.Message.{AssistantMessage, ToolMessage, UserMessage}
import sttp.ai.openai.requests.completions.chat.message.Tool.FunctionTool
import sttp.tapir.generic.auto.*

case class Passenger(name: String, age: Int)

object Passenger:
  given SnakePickle.Reader[Passenger] = SnakePickle.macroR[Passenger]

case class FlightDetails(passenger: Passenger, departureCity: String, destinationCity: String)

object FlightDetails:
  given SnakePickle.Reader[FlightDetails] = SnakePickle.macroR[FlightDetails]

case class BookedFlight(confirmationNumber: String, status: String)

object BookedFlight:
  given SnakePickle.Writer[BookedFlight] = SnakePickle.macroW[BookedFlight]

object Main:
  def main(args: Array[String]): Unit =
    val apiKey = System.getenv("OPENAI_KEY")
    val openAI = OpenAISyncClient(apiKey)

    val initialRequestMessage = Seq(UserMessage(content = TextContent("I want to book a flight from London to Tokyo for Jane Doe, age 34")))

    // Request created using FunctionTool.withSchema, all we need to do here is just define the type. The schema is automatically generated using a macro, available via the `sttp.tapir.generic.auto.*` import.
    val givenRequest = ChatBody(
      model = GPT4oMini,
      messages = initialRequestMessage,
      tools = Some(Seq(
        FunctionTool.withSchema[FlightDetails](
          name = "book_flight",
          description = Some("Books a flight for a passenger with full details")))
      )
    )

    val initialRequestResult = openAI.createChatCompletion(givenRequest)

    println(initialRequestResult.choices)
    /*
      List(
        Choices(
          Message(
            null,
            None,
            List(
              FunctionToolCall(
                Some(call_XZNvfldLQTa1f7aMInswpTMS),
                FunctionCall(
                  {
                    "passenger": {
                      "name": "Jane Doe",
                      "age": 34
                    },
                    "departureCity": "London",
                    "destinationCity": "Tokyo"
                  },
                  Some(book_flight)
                )
              )
            ),
            Assistant,
            None,
            None
          ),
          tool_calls,
          0,
          None
        )
      )
      */

    // Helper function that mimics external function definition
    def bookFlight(flightDetails: FlightDetails): BookedFlight =
      println(flightDetails)
      BookedFlight(confirmationNumber = "123456", status = "confirmed")

    // Tool calls list (in this example it is just single tool call, but there may be multiple), which is necessary to build message list for second request.
    val toolCalls = initialRequestResult.choices.head.message.toolCalls

    val functionToolCall = toolCalls.head match
      case functionToolCall: FunctionToolCall => functionToolCall

    // Function arguments are manually deserialized, 'bookFlight' function mimic external function definition.
    val bookedFlight = bookFlight(SnakePickle.read[FlightDetails](functionToolCall.function.arguments))

    val secondRequest = givenRequest.copy(
      messages = initialRequestMessage
        :+ AssistantMessage(content = "", toolCalls = toolCalls)
        // ToolMessage created using object instead of String with Json representation of object.
        :+ ToolMessage(toolCallId = functionToolCall.id.get, content = bookedFlight)
    )

    val finalResult = openAI.createChatCompletion(secondRequest)

    println(finalResult.choices)
    /*
      List(
        Choices(
          Message(
            "The flight from London to Tokyo for Jane Doe, age 34, has been successfully booked. The confirmation number is **123456** and the status is **confirmed**.",
            None,
            List(),
            Assistant,
            None,
            None
          ),
          stop,
          0,
          None
        )
      )
      */
```

## Contributing

If you have a question, or hit a problem, feel free to post on our community https://softwaremill.community/c/open-source/

Or, if you encounter a bug, something is unclear in the code or documentation, don't hesitate and open an issue on GitHub.

For running integration tests against the real OpenAI API, see [Integration Testing Guide](INTEGRATION_TESTING.md).

## Commercial Support

We offer commercial support for sttp and related technologies, as well as development services. [Contact us](https://softwaremill.com) to learn more about our offer!

## Copyright

Copyright (C) 2023-2025 SoftwareMill [https://softwaremill.com](https://softwaremill.com).
