# Streaming

Every effect system supported by sttp-ai exposes the same extension method — `createStreamedInteraction` — added to `GeminiClient`, taking an `InteractionRequest`. It turns on server-sent-event streaming (`stream = true`) and decodes each event into an `InteractionStreamEvent`.

| Effect System | Import | Element type |
|---------------|--------|--------------|
| **fs2** (cats-effect) | `sttp.ai.gemini.streaming.fs2.GeminiFs2Streaming.*` | `Stream[F, InteractionStreamEvent]` |
| **ZIO** | `sttp.ai.gemini.streaming.zio.GeminiZioStreaming.*` | `Stream[Throwable, InteractionStreamEvent]` |
| **Akka Streams** (Scala 2.13 only) | `sttp.ai.gemini.streaming.akka.GeminiAkkaStreaming.*` | `Source[InteractionStreamEvent, Any]` |
| **Pekko Streams** | `sttp.ai.gemini.streaming.pekko.GeminiPekkoStreaming.*` | `Source[InteractionStreamEvent, Any]` |
| **Ox** (Scala 3 only) | `sttp.ai.gemini.streaming.ox.*` | `Flow[Either[Exception, InteractionStreamEvent]]` |

`InteractionStreamEvent.eventType` is deliberately an open `String`, not an enum: the API documents `interaction.completed` and `error`, plus additional incremental event types that may be added over time. Match on the value you care about and ignore the rest — an unrecognized `eventType` must not break your consumer.

```scala
case class InteractionStreamEvent(
  eventType: String,
  eventId: Option[String] = None,
  interaction: Option[InteractionResponse] = None,
  error: Option[StreamError] = None,
  metadata: Option[StreamMetadata] = None
)
```

## Using fs2 (cats-effect)

```scala
//> using dep com.softwaremill.sttp.ai::gemini:0.11.0
//> using dep com.softwaremill.sttp.ai::fs2:0.11.0

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.streaming.fs2.GeminiFs2Streaming.*
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

object FS2StreamingExample:
  def main(args: Array[String]): Unit =
    val client = GeminiClient.fromEnv
    val request = InteractionRequest.simple("gemini-2.5-flash", "Write a haiku about the sea.")

    val program = HttpClientFs2Backend.resource[IO]().use { backend =>
      client
        .createStreamedInteraction[IO](request)
        .send(backend)
        .map(_.body)
        .flatMap {
          case Left(error) => IO.println(s"Error: ${error.getMessage}")
          case Right(stream) =>
            stream
              .evalTap { event =>
                IO.println(event.eventType) *> IO.whenA(event.eventType == "interaction.completed") {
                  IO.println(event.interaction.map(_.outputText).getOrElse(""))
                }
              }
              .compile
              .drain
        }
    }

    program.unsafeRunSync()
```

## Using Ox (Scala 3)

The Ox extension is defined directly on `GeminiClient`, returning a plain `Request` (not a `StreamRequest`, since Ox streams from a blocking `InputStream` rather than a stream capability type) whose body is a `Flow[Either[Exception, InteractionStreamEvent]]` — deserialization failures of individual events surface per-element as a `Left`, instead of failing the whole flow. Any sttp4 backend works, e.g. the plain blocking `DefaultSyncBackend`.

```scala
//> using dep com.softwaremill.sttp.ai::gemini:0.11.0
//> using dep com.softwaremill.sttp.ai::ox:0.11.0

import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.streaming.ox.*
import sttp.client4.DefaultSyncBackend

object OxStreamingExample:
  def main(args: Array[String]): Unit =
    val client = GeminiClient.fromEnv
    val request = InteractionRequest.simple("gemini-2.5-flash", "Write a haiku about the sea.")

    val backend = DefaultSyncBackend()
    try
      client.createStreamedInteraction(request).send(backend).body match {
        case Left(error) => println(s"Error: ${error.getMessage}")
        case Right(flow) =>
          flow.runForeach {
            case Right(event) => println(event.eventType)
            case Left(error)  => println(s"Deserialization error: ${error.getMessage}")
          }
      }
    finally backend.close()
```
