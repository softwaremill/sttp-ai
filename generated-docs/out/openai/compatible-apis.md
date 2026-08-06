# OpenAI-compatible APIs: Azure OpenAI, Ollama, Grok, Groq, OpenRouter

Any provider exposing an OpenAI-compatible endpoint works with the OpenAI client — pass the provider's base URL as the second argument. Named examples below; the same pattern applies to any other compatible provider.

## Azure OpenAI

Azure OpenAI's API-key authentication uses an `api-key: <key>` header instead of the standard `Authorization: Bearer <key>`. Pass `AuthScheme.AzureApiKey` together with your deployment's endpoint URL (including the `api-version` query parameter — it is preserved on every request):

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.7.0

import sttp.model.Uri.*
import sttp.ai.openai.{AuthScheme, OpenAISyncClient}
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

object Main:
  def main(args: Array[String]): Unit =
    val openAI: OpenAISyncClient = OpenAISyncClient(
      System.getenv("AZURE_OPENAI_API_KEY"),
      uri"https://my-resource.openai.azure.com/openai/deployments/gpt-4o?api-version=2024-10-21",
      AuthScheme.AzureApiKey
    )

    val chatRequestBody: ChatBody = ChatBody(
      // for deployment-scoped endpoints the model is selected by the URL's deployment path;
      // the body's model field is ignored by Azure but required by the API shape
      model = ChatCompletionModel.CustomChatCompletionModel("gpt-4o"),
      messages = Seq(
        Message.User(
          content = Content.TextContent("Hello!")
        )
      )
    )

    val chatResponse: ChatResponse = openAI.createChatCompletion(chatRequestBody)
    println(chatResponse)
```

The newer Azure `/openai/v1/` endpoint also accepts standard Bearer authentication with the API key, so it works like any other OpenAI-compatible provider — pass `uri"https://my-resource.openai.azure.com/openai/v1"` as the base URL and keep the default auth scheme.

## Ollama

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.7.0

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

## Grok

[Grok](https://x.ai) is xAI's model family, served from an OpenAI-compatible endpoint:

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.7.0

import sttp.model.Uri.*
import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

object Main:
  def main(args: Array[String]): Unit =
    val openAI: OpenAISyncClient = OpenAISyncClient(System.getenv("XAI_API_KEY"), uri"https://api.x.ai/v1")

    val bodyMessages: Seq[Message] = Seq(
      Message.User(
        content = Content.TextContent("Hello!"),
      )
    )

    val chatRequestBody: ChatBody = ChatBody(
      model = ChatCompletionModel.CustomChatCompletionModel("grok-4"),
      messages = bodyMessages
    )

    // be aware that calling `createChatCompletion` may throw an OpenAIException
    // e.g. AuthenticationException, RateLimitException and many more
    val chatResponse: ChatResponse = openAI.createChatCompletion(chatRequestBody)

    println(chatResponse)
```

## Groq

[Groq](https://groq.com) runs open models (Llama, Gemma, ...) on its LPU hardware, exposed via an OpenAI-compatible endpoint:

Groq with cats-effect based backend:

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.7.0
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
    val apiKey = System.getenv("GROQ_API_KEY")
    val openAI = new OpenAI(apiKey, uri"https://api.groq.com/openai/v1")

    val bodyMessages: Seq[Message] = Seq(
      Message.User(
        content = Content.TextContent("Hello!"),
      )
    )

    val chatRequestBody: ChatBody = ChatBody(
      model = ChatCompletionModel.CustomChatCompletionModel("llama-3.1-8b-instant"),
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
      "llama-3.1-8b-instant",
      "chat.completion",
      Usage(16, 21, 37),
      Some("fp_f0c35fc854")
    )
  */
```

## OpenRouter

OpenRouter with sync backend:

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.7.0

import sttp.model.Uri.*
import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

object Main:
  def main(args: Array[String]): Unit =
    val apiKey = System.getenv("OPENROUTER_API_KEY")
    val openAI: OpenAISyncClient = OpenAISyncClient(apiKey, uri"https://openrouter.ai/api/v1")

    val bodyMessages: Seq[Message] = Seq(
      Message.User(
        content = Content.TextContent("Hello!"),
      )
    )

    val chatRequestBody: ChatBody = ChatBody(
      // OpenRouter model identifiers are "provider/model", see https://openrouter.ai/models
      model = ChatCompletionModel.CustomChatCompletionModel("openai/gpt-4o-mini"),
      messages = bodyMessages
    )

    // be aware that calling `createChatCompletion` may throw an OpenAIException
    // e.g. AuthenticationException, RateLimitException and many more
    val chatResponse: ChatResponse = openAI.createChatCompletion(chatRequestBody)

    println(chatResponse)
  /*
    ChatResponse(
      "gen-1234567890-abcdefghijklmnopqrstuvwx",
      List(
        Choices(
          Message(Assistant, """Hello there! How can I help you today?""", List(), None),
          "stop",
          0
        )
      ),
      1714663831,
      "openai/gpt-4o-mini",
      "chat.completion",
      Usage(10, 10, 20),
      None
    )
  */
```

## Reasoning models

Reasoning models served through OpenAI-compatible APIs return their chain-of-thought in a dedicated response field. Providers use two
spellings on the wire — `reasoning_content` (DeepSeek, Qwen, vLLM-served models, Ollama) or `reasoning` (OpenRouter, Groq, xAI) — and both
are decoded into the same `reasoningContent: Option[String]` field, on the response `Message` (non-streaming) and on each streamed `Delta`
(accumulate the deltas exactly like `content`):

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.7.0

import sttp.model.Uri.*
import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

object Main:
  def main(args: Array[String]): Unit =
    val openAI: OpenAISyncClient = OpenAISyncClient("ollama", uri"http://localhost:11434/v1")

    val chatRequestBody: ChatBody = ChatBody(
      // assuming one has already executed `ollama pull qwen3` in console
      model = ChatCompletionModel.CustomChatCompletionModel("qwen3"),
      messages = Seq(
        Message.User(
          content = Content.TextContent("What is 2+2?")
        )
      )
    )

    val chatResponse: ChatResponse = openAI.createChatCompletion(chatRequestBody)
    val message = chatResponse.choices.head.message

    println(message.reasoningContent) // Some("Okay, the user is asking what 2 plus 2 is...")
    println(message.content) // "4"
```

The answer itself stays in `content`; `reasoningContent` is `None` for models (or providers) that don't emit reasoning. Reasoning is never
sent back in requests — some providers (e.g. DeepSeek) reject request messages carrying `reasoning_content`.

## Extra body parameters (vLLM and other extensions)

Some OpenAI-compatible backends — vLLM in particular — accept request parameters that aren't part of the official OpenAI API and have no
typed field on `ChatBody`, `CompletionsBody`, or `EmbeddingsBody` (e.g. vLLM's `guided_json` or `top_k`). Use `extraBody` to merge arbitrary
JSON values into the top level of the serialized request, alongside the typed fields:

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.7.0

import io.circe.Json
import sttp.model.Uri.*
import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

object Main:
  def main(args: Array[String]): Unit =
    val openAI: OpenAISyncClient = OpenAISyncClient("vllm", uri"http://localhost:8000/v1")

    val bodyMessages: Seq[Message] = Seq(
      Message.User(
        content = Content.TextContent("List three colors as JSON."),
      )
    )

    val chatRequestBody: ChatBody = ChatBody(
      model = ChatCompletionModel.CustomChatCompletionModel("meta-llama/Llama-3.1-8B-Instruct"),
      messages = bodyMessages,
      // vLLM-specific parameters with no typed field on ChatBody, merged into the top-level request JSON:
      extraBody = Map(
        "guided_json" -> Json.obj(
          "type" -> Json.fromString("object"),
          "properties" -> Json.obj("colors" -> Json.obj("type" -> Json.fromString("array")))
        ),
        "top_k" -> Json.fromInt(40)
      )
    )

    val chatResponse: ChatResponse = openAI.createChatCompletion(chatRequestBody)

    println(chatResponse)
```

## Client implementations

The client comes in two flavours — the blocking `OpenAISyncClient` and the request-based `OpenAI` — see [OpenAI API basics](basics.md).

To customize a request when using the `OpenAISyncClient`, e.g. by adding a header, or changing the timeout (via request options), you can use the `.customizeRequest` method on the client.

Example below uses `HttpClientCatsBackend` as a backend, make sure to [add it to the dependencies](https://sttp.softwaremill.com/en/latest/backends/catseffect.html)
or use backend of your choice.

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.7.0
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
      model = ChatCompletionModel.GPT4oMini,
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
      gpt-4o-mini,
      Usage(10,10,20),
      List(
        Choices(
          Message(assistant, Hello there! How can I assist you today?), stop, 0)
        )
      )
    )
  */
```
