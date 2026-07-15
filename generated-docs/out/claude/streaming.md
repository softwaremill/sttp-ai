# Streaming

## Using fs2 (cats-effect)

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

## Using ZIO

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

## Using Ox (Scala 3)

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

