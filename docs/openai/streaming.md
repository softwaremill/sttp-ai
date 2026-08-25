# Streaming

The Chat Completions API and the Responses API can both stream responses as server-sent events. Add the streaming module for your chosen library — [fs2](https://fs2.io), [ZIO](https://zio.dev), [Akka Streams](https://doc.akka.io/libraries/akka-core/current/stream/) / [Pekko Streams](https://pekko.apache.org/docs/pekko/current/stream/), or [Ox](https://github.com/softwaremill/ox) — and extension methods become available on the `OpenAI` client: `createStreamedChatCompletion`, returning a stream of `ChatChunkResponse` chunks (covered first, below), and `createStreamedModelResponse`, returning a stream of `ResponsesStreamEvent` events (see [Streaming the Responses API](#streaming-the-responses-api)).

## Using fs2 (cats-effect)

```scala
// sbt dependency
"com.softwaremill.sttp.ai" %% "fs2" % "@VERSION@"

// import
import sttp.ai.openai.streaming.fs2.*
```

The example below uses `HttpClientFs2Backend` as a backend:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::fs2:@VERSION@

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
      Message.User(
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

## Using ZIO

```scala
// sbt dependency
"com.softwaremill.sttp.ai" %% "zio" % "@VERSION@"

// import
import sttp.ai.openai.streaming.zio.*
```

The example below uses `HttpClientZioBackend` as a backend:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::zio:@VERSION@

import sttp.ai.openai.OpenAI
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*
import sttp.ai.openai.streaming.zio.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import zio.*

object Main extends ZIOAppDefault:
  override def run =
    val openAI = new OpenAI(java.lang.System.getenv("OPENAI_KEY"))

    val chatRequestBody: ChatBody = ChatBody(
      model = ChatCompletionModel.GPT4oMini,
      messages = Seq(Message.User(Content.TextContent("Hello!")))
    )

    ZIO.scoped {
      for {
        backend <- HttpClientZioBackend.scoped()
        response <- openAI.createStreamedChatCompletion(chatRequestBody).send(backend)
        _ <- response.body match {
          case Left(exception) => Console.printLine(exception.getMessage)
          case Right(stream)   => stream.tap(chunk => Console.printLine(chunk.toString)).runDrain
        }
      } yield ()
    }
```

## Using Pekko Streams

```scala
// sbt dependency
"com.softwaremill.sttp.ai" %% "pekko" % "@VERSION@"

// import
import sttp.ai.openai.streaming.pekko.*
```

The example below uses `PekkoHttpBackend` as a backend:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::pekko:@VERSION@

import org.apache.pekko.actor.ActorSystem
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import sttp.client4.pekkohttp.PekkoHttpBackend
import sttp.ai.openai.OpenAI
import sttp.ai.openai.streaming.pekko.*
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

object Main:
  def main(args: Array[String]): Unit =
    implicit val system: ActorSystem = ActorSystem("openai-pekko-example")
    implicit val ec: ExecutionContext = system.dispatcher

    val apiKey = System.getenv("OPENAI_KEY")
    val openAI = new OpenAI(apiKey)
    val backend = PekkoHttpBackend.usingActorSystem(system)

    val bodyMessages: Seq[Message] = Seq(
      Message.User(
        content = Content.TextContent("Hello!"),
      )
    )

    val chatRequestBody: ChatBody = ChatBody(
      model = ChatCompletionModel.GPT35Turbo,
      messages = bodyMessages
    )

    val program: Future[Unit] =
      openAI
        .createStreamedChatCompletion(chatRequestBody)
        .send(backend)
        .map(_.body)
        .flatMap {
          case Left(exception) => Future.successful(println(exception.getMessage))
          case Right(stream)   => stream.runForeach(println).map(_ => ())
        }

    try Await.result(program, 30.seconds)
    finally (system.terminate(): Unit)
```

## Using Akka Streams (Scala 2.13)

```scala
// sbt dependency (Scala 2.13 only)
"com.softwaremill.sttp.ai" %% "akka" % "@VERSION@"

// import
import sttp.ai.openai.streaming.akka.*
```

The `akka` module mirrors the Pekko example above: use `AkkaHttpBackend` in place of `PekkoHttpBackend` and consume the resulting `Source[ChatChunkResponse, _]` the same way.

## Using Ox (Scala 3)

Direct-style streaming, without an effect system:

```scala
// sbt dependency
"com.softwaremill.sttp.ai" %% "ox" % "@VERSION@"

// import
import sttp.ai.openai.streaming.ox.*
```

Example code:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::ox:@VERSION@

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
      Message.User(
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

## Streaming the Responses API

Unlike Chat Completions, which streams one repeated chunk shape, the Responses API streams **typed** events: `response.created`,
`response.output_item.added`, `response.output_text.delta`, `response.completed`, `error`, and around fifty more. They are modelled as the
`ResponsesStreamEvent` sealed trait, so you pattern-match for the cases you care about and ignore the rest. A few things worth knowing:

- `stream = true` is set for you, whatever `ResponsesRequestBody.stream` says.
- There is no `[DONE]` sentinel: the stream ends with `response.completed` (or `response.failed` / `response.incomplete`) and the
  connection closing. A sentinel sent by an OpenAI-compatible provider is skipped rather than treated as end-of-stream.
- An event type this version of the library does not know about decodes to `ResponsesStreamEvent.Unknown`, carrying the raw JSON, so a
  newly-introduced event type cannot break a running stream.
- For a response created with `background = true`, `resumeStreamedModelResponse` replays the events of a stored response, optionally from
  `GetResponseQueryParameters.startingAfter` — useful for picking up an interrupted stream.

### Using fs2 (cats-effect)

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::fs2:@VERSION@

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.ai.openai.OpenAI
import sttp.ai.openai.OpenAIExceptions.OpenAIException
import sttp.ai.openai.requests.responses.ResponsesModel.GPT4oMini
import sttp.ai.openai.requests.responses.{ResponsesRequestBody, ResponsesStreamEvent}
import sttp.ai.openai.streaming.fs2.*

object Main:
  def main(args: Array[String]): Unit =
    val openAI = new OpenAI(System.getenv("OPENAI_KEY"))

    val requestBody = ResponsesRequestBody(
      model = Some(GPT4oMini),
      input = Some(Left("Write a haiku about streaming."))
    )

    val program = HttpClientFs2Backend.resource[IO]().use { backend =>
      val response: IO[Either[OpenAIException, Stream[IO, ResponsesStreamEvent]]] =
        openAI
          .createStreamedModelResponse[IO](requestBody)
          .send(backend)
          .map(_.body)

      response.flatMap {
        case Left(exception) => IO.println(exception.getMessage)
        case Right(stream) =>
          stream
            .collect { case delta: ResponsesStreamEvent.OutputTextDelta => delta.delta }
            .evalTap(IO.print)
            .compile
            .drain
      }
    }

    program.unsafeRunSync()
```

### Using Ox (Scala 3)

The `ox` module keeps decoding failures in the stream, so each element is an `Either`:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::ox:@VERSION@

import ox.*
import ox.either.orThrow
import sttp.client4.DefaultSyncBackend
import sttp.ai.openai.OpenAI
import sttp.ai.openai.requests.responses.ResponsesModel.GPT4oMini
import sttp.ai.openai.requests.responses.{ResponsesRequestBody, ResponsesStreamEvent}
import sttp.ai.openai.streaming.ox.*

object Main extends OxApp:
  override def run(args: Vector[String])(using Ox): ExitCode =
    val openAI = new OpenAI(System.getenv("OPENAI_KEY"))

    val requestBody = ResponsesRequestBody(
      model = Some(GPT4oMini),
      input = Some(Left("Write a haiku about streaming."))
    )

    val backend = useCloseableInScope(DefaultSyncBackend())
    openAI
      .createStreamedModelResponse(requestBody)
      .send(backend)
      .body // this gives us an Either[OpenAIException, Flow[Either[Exception, ResponsesStreamEvent]]]
      .orThrow // we choose to throw any exceptions and fail the whole app
      .runForeach { event =>
        event.orThrow match
          case delta: ResponsesStreamEvent.OutputTextDelta => print(delta.delta)
          case ResponsesStreamEvent.Completed(response, _) => println(s"\ndone: ${response.status}")
          case _                                           => ()
      }

    ExitCode.Success
```

### Using ZIO, Pekko Streams or Akka Streams

`createStreamedModelResponse` has the same shape as `createStreamedChatCompletion` in these modules — substitute
`ResponsesRequestBody` for `ChatBody` and `ResponsesStreamEvent` for `ChatChunkResponse` in the corresponding example above.

See also the [ChatProxy](https://github.com/softwaremill/sttp-ai/blob/master/examples/src/main/scala/examples/ChatProxy.scala) example application.
