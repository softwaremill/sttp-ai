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

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

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

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

val chatRequestBody = ChatBody(
  model = ChatCompletionModel.GPT5,
  messages = Seq(Message.User(Content.TextContent("Hello!"))),
  maxCompletionTokens = Some(1000)
)
```

## Compacting long conversations (Responses API)

Long conversations, such as agent runs with many tool calls, can outgrow the model's context window. The Responses API can [compact](https://developers.openai.com/api/docs/guides/compaction) the context into a `compaction` item: an encrypted summary that stands in for the conversation before it. You can request compaction in three ways:

- **Automatically**: set `contextManagement` on the request. Once the context crosses `compactThreshold` tokens, the server compacts it and adds a compaction item to the response output.
- **On demand**: end the input with `Input.CompactionTrigger()`. The response output then holds a single compaction item.
- **Standalone**: call `compactConversation`, which compacts without generating a response. The result holds the retained user messages followed by a compaction item; `toInput` turns it into the input for the next request (only the text of user messages is kept).

With `store` enabled (the default) and `previousResponseId`, OpenAI keeps compaction items on its side. When you keep the conversation yourself and send the whole input on every request (`store = Some(false)`), pass each returned compaction item back with `toInput`. Items before the latest compaction item can be dropped from later requests.

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.responses.{CompactRequestBody, ResponsesModel, ResponsesRequestBody}
import sttp.ai.openai.requests.responses.ResponsesRequestBody.{ContextManagement, Input}
import sttp.ai.openai.requests.responses.ResponsesRequestBody.Input.InputContentItem.InputText
import sttp.ai.openai.requests.responses.ResponsesResponseBody.OutputItem

val openAI = OpenAISyncClient(System.getenv("OPENAI_KEY"))

def userMessage(text: String): Input = Input.InputMessage(List(InputText(text)), role = "user", status = None)
def request(input: List[Input]) =
  ResponsesRequestBody(model = Some(ResponsesModel.GPT5), store = Some(false), input = Some(Right(input)))

val history: List[Input] = List(userMessage("Let's plan the database migration."))

// on demand: the trigger must be the last input item
val compacted = openAI.createModelResponse(request(history :+ Input.CompactionTrigger()))
val compaction = compacted.output.collect { case item: OutputItem.Compaction => item.toInput }

// continue from the compaction item instead of the full history
val next = openAI.createModelResponse(request(compaction :+ userMessage("What is the first step?")))

// automatically: the server compacts once the context grows past 200k tokens
val automatic = request(history).copy(
  contextManagement = Some(List(ContextManagement.Compaction(compactThreshold = Some(200000))))
)

// standalone: compact without generating a response
val window = openAI.compactConversation(CompactRequestBody(model = ResponsesModel.GPT5, input = Some(Right(history))))
val afterWindow = openAI.createModelResponse(request(window.toInput :+ userMessage("What is the first step?")))
```
