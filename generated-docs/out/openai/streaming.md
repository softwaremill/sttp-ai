# Streaming

The Chat Completions API can stream responses as server-sent events. Add the streaming module for your chosen library — [fs2](https://fs2.io), [ZIO](https://zio.dev), [Akka Streams](https://doc.akka.io/libraries/akka-core/current/stream/) / [Pekko Streams](https://pekko.apache.org/docs/pekko/current/stream/), or [Ox](https://github.com/softwaremill/ox) — and an extension method `createStreamedChatCompletion` becomes available on the `OpenAI` client, returning a stream of `ChatChunkResponse` events.

## Using fs2 (cats-effect)

```scala
// sbt dependency
"com.softwaremill.sttp.ai" %% "fs2" % "0.8.0"

// import
import sttp.ai.openai.streaming.fs2.*
```

The example below uses `HttpClientFs2Backend` as a backend:

```scala
//> using dep com.softwaremill.sttp.ai::fs2:0.8.0

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
"com.softwaremill.sttp.ai" %% "zio" % "0.8.0"

// import
import sttp.ai.openai.streaming.zio.*
```

The example below uses `HttpClientZioBackend` as a backend:

```scala
//> using dep com.softwaremill.sttp.ai::zio:0.8.0

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
"com.softwaremill.sttp.ai" %% "pekko" % "0.8.0"

// import
import sttp.ai.openai.streaming.pekko.*
```

The example below uses `PekkoHttpBackend` as a backend:

```scala
//> using dep com.softwaremill.sttp.ai::pekko:0.8.0

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
"com.softwaremill.sttp.ai" %% "akka" % "0.8.0"

// import
import sttp.ai.openai.streaming.akka.*
```

The `akka` module mirrors the Pekko example above: use `AkkaHttpBackend` in place of `PekkoHttpBackend` and consume the resulting `Source[ChatChunkResponse, _]` the same way.

## Using Ox (Scala 3)

Direct-style streaming, without an effect system:

```scala
// sbt dependency
"com.softwaremill.sttp.ai" %% "ox" % "0.8.0"

// import
import sttp.ai.openai.streaming.ox.*
```

Example code:

```scala
//> using dep com.softwaremill.sttp.ai::ox:0.8.0

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

See also the [ChatProxy](https://github.com/softwaremill/sttp-ai/blob/master/examples/src/main/scala/examples/ChatProxy.scala) example application.
