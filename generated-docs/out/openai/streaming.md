# Streaming

## Create completion with streaming:

To enable streaming support for the Chat Completion API using server-sent events, you must include the appropriate
dependency for your chosen streaming library. We provide support for the following libraries: _fs2_, _ZIO_, _Akka / Pekko Streams_ and _Ox_.

For example, to use `fs2` add the following dependency & import:

```scala
// sbt dependency
"com.softwaremill.sttp.ai" %% "fs2" % "0.5.1+19-83e9d91a+20260714-1032-SNAPSHOT"

// import 
import sttp.ai.openai.streaming.fs2.*
```

Example below uses `HttpClientFs2Backend` as a backend:

```scala
//> using dep com.softwaremill.sttp.ai::fs2:0.5.1+19-83e9d91a+20260714-1032-SNAPSHOT

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

To use direct-style streaming (requires Scala 3) add the following dependency & import:

```scala
// sbt dependency
"com.softwaremill.sttp.ai" %% "ox" % "0.5.1+19-83e9d91a+20260714-1032-SNAPSHOT"

// import 
import sttp.ai.openai.streaming.ox.*
```

Example code:

```scala
//> using dep com.softwaremill.sttp.ai::ox:0.5.1+19-83e9d91a+20260714-1032-SNAPSHOT

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

See also the [ChatProxy](https://github.com/softwaremill/sttp-openai/blob/master/examples/src/main/scala/examples/ChatProxy.scala) example application.
