# OpenAI-compatible APIs

## To use Ollama or Grok (OpenAI-compatible APIs)

Ollama with sync backend:

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.5.1+17-73bc7345+20260714-1005-SNAPSHOT

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
      Message.User(
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

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.5.1+17-73bc7345+20260714-1005-SNAPSHOT
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
      Message.User(
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

### Available client implementations:

* `OpenAISyncClient` which provides high-level methods to interact with OpenAI. All the methods send requests synchronously and are blocking, might throw `OpenAIException`
* `OpenAI` which provides raw sttp-client4 `Request`s and parses `Response`s as `Either[OpenAIException, A]`

If you want to make use of other effects, you have to use `OpenAI` and pass the chosen backend directly to `request.send(backend)` function.

To customize a request when using the `OpenAISyncClient`, e.g. by adding a header, or changing the timeout (via request options), you can use the `.customizeRequest` method on the client.

Example below uses `HttpClientCatsBackend` as a backend, make sure to [add it to the dependencies](https://sttp.softwaremill.com/en/latest/backends/catseffect.html)
or use backend of your choice.

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.5.1+17-73bc7345+20260714-1005-SNAPSHOT
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
      Message.User(
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
