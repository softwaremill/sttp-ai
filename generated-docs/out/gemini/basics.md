# Gemini API

This module provides **native support for Google's Gemini API**, built on the [Interactions API](https://ai.google.dev/gemini-api/docs) — Google's newer, agent-oriented endpoint (`/v1beta/interactions`). It is a separate module from `openai` and `claude`, with its own request/response models and exception hierarchy, following the same shape as the Claude module.

## Gemini Features

- **Native Interactions API support** — direct integration, not a compatibility layer
- **Step-based conversation model** — `Step` (`UserInput`, `ModelOutput`, `FunctionCall`, `FunctionResult`) and rich `Content` (text, image, audio, video, document)
- **Server-side or stateless conversations** — `store`/`previousInteractionId` for server-side history, or `InteractionInput.StepsInput` for full stateless replay; see [Interactions](interactions.md)
- **Tool calling** — custom `Tool.Function` declarations plus Google-hosted `Tool.GoogleSearch` and `Tool.CodeExecution`; see [Tool calling](tool-calling.md)
- **Structured outputs** — `ResponseFormat.JsonSchema`, including a typed `createInteractionAs[T]` helper; see [Structured outputs](structured-outputs.md)
- **Streaming** — Server-Sent Events streaming for fs2, ZIO, Akka, Pekko, and Ox; see [Streaming](streaming.md)
- **Agent loop integration** — `GeminiAgent` plugs into the shared `sttp.ai.core.agent` framework; see the [agent loop](../agents/quickstart.md)
- **Comprehensive error handling** — Gemini-specific exception hierarchy; see [Models and error handling](models-and-errors.md)
- **Cross-platform** — Scala 2.13 and Scala 3 on the JVM, plus Scala Native (Scala 3)

## Basic Usage (Gemini)

```scala
//> using dep com.softwaremill.sttp.ai::gemini:0.11.0

import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.requests.InteractionRequest

object Main:
  def main(args: Array[String]): Unit =
    // Reads the GEMINI_API_KEY environment variable
    val gemini = GeminiSyncClient.fromEnv
    try {
      val request = InteractionRequest.simple(
        model = "gemini-2.5-flash",
        text = "Hello Gemini! What's the weather like today?"
      )

      // Throws a GeminiException subclass on error
      val response = gemini.createInteraction(request)

      println(response.outputText)
      println(s"Usage: ${response.usage}")
    } finally gemini.close()
```

`GeminiSyncClient.fromEnv` reads the `GEMINI_API_KEY` environment variable (and, optionally, `GEMINI_BASE_URL` to point at a different endpoint). Pass a `GeminiConfig` explicitly if you'd rather not use environment variables.

## Async Usage

For non-blocking code, use `GeminiClient` directly with an sttp backend of your choice. Every method returns a plain sttp `Request` whose body is an `Either` — nothing is thrown, and you choose when and how to `.send` it. `createInteraction`, `getInteraction`, and `cancelInteraction` return `Either[GeminiException, InteractionResponse]`; `deleteInteraction` returns `Either[GeminiException, Unit]`.

```scala
//> using dep com.softwaremill.sttp.ai::gemini:0.11.0

import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.requests.InteractionRequest
import sttp.client4.*

object AsyncMain:
  def main(args: Array[String]): Unit =
    val backend: SyncBackend = DefaultSyncBackend()
    val client = GeminiClient.fromEnv

    val request = InteractionRequest.simple("gemini-2.5-flash", "Say hello in one sentence.")

    val response = client.createInteraction(request).send(backend)

    response.body match {
      case Right(interaction) => println(interaction.outputText)
      case Left(error)        => println(s"Gemini API error: ${error.getMessage}")
    }

    backend.close()
```

The example above uses the synchronous sttp backend for brevity, but `GeminiClient` works with any sttp4 backend — cats-effect, ZIO, Akka/Pekko, Ox, etc. — by swapping the backend and calling `.send(backend)` in the corresponding effect.

## Gemini Configuration

```scala
case class GeminiConfig(
  apiKey: String,                                                        // Your Gemini API key
  baseUrl: Uri = Uri.unsafeParse("https://generativelanguage.googleapis.com"),
  timeout: Duration = 10.minutes,                                        // Request timeout
  maxRetries: Int = 3,                                                   // Max retry attempts, honored by GeminiSyncClient
  organization: Option[String] = None                                    // Unused by Gemini, present for parity
)
```

**Environment Variables:**
- `GEMINI_API_KEY` — Your API key (required)
- `GEMINI_BASE_URL` — Custom base URL (optional)

**Next steps:** see [Interactions](interactions.md) for the conversation lifecycle and multi-turn patterns, [Tool calling](tool-calling.md) and [Structured outputs](structured-outputs.md) for advanced request features, [Streaming](streaming.md) for SSE support, and [Models and errors](models-and-errors.md) for the model catalogue and exception hierarchy.
