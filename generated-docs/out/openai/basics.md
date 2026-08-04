# OpenAI API basics

This module provides direct access to the [OpenAI API](https://platform.openai.com/docs/api-reference). Examples are runnable using [scala-cli](https://scala-cli.virtuslab.org).

## Available features

- ✅ **Chat completions** — see basic usage below
- ✅ **Streaming** — [server-sent events streaming](streaming.md) for fs2, ZIO, Akka/Pekko Streams, and Ox
- ✅ **Structured outputs** — [JSON-schema-constrained responses](structured-outputs.md) parsed into case classes
- ✅ **Tool calling** — [function tools](tool-calling.md) with derived parameter schemas
- ✅ **OpenAI-compatible providers** — [Ollama, Groq, OpenRouter, vLLM and others](compatible-apis.md)
- ✅ **Agent loop** — [autonomous tool-calling agents](../agents/quickstart.md)
- ✅ **Full API surface** — completions, embeddings, audio, images, files, fine-tuning, batches, assistants, moderations, and more
- ✅ **Cross-platform** — Scala 2.13 and Scala 3

## Sync and async clients

- `OpenAISyncClient` — high-level and blocking: methods return the response directly and throw an `OpenAIException` subclass on error. The recommended default, used in most examples in these docs.
- `OpenAI` — returns raw sttp-client4 `Request`s and parses responses as `Either[OpenAIException, A]`. Pair it with the sttp backend of your choice (cats-effect, ZIO, Akka/Pekko, Ox) — see [streaming](streaming.md) for effectful examples.

## Basic usage

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.6.0

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
      Message.User(
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

## Token limits on reasoning models (GPT-5, o-series)

Newer OpenAI reasoning models (GPT-5, o1, o3, ...) reject the `max_tokens` parameter with `Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead.` For these models leave `maxTokens = None` and set `maxCompletionTokens`:

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.6.0

import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

val chatRequestBody = ChatBody(
  model = ChatCompletionModel.GPT5,
  messages = Seq(Message.User(Content.TextContent("Hello!"))),
  maxCompletionTokens = Some(1000)
)
```
