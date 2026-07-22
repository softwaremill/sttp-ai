# OpenAI API

OpenAI API Official Documentation: https://platform.openai.com/docs/api-reference/completions

Examples are runnable using [scala-cli](https://scala-cli.virtuslab.org).

## Basic Usage (OpenAI)

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.5.4

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

> **Token limits on GPT-5 / reasoning models:** newer OpenAI reasoning models (GPT-5, o1, o3, ...) reject the `max_tokens`
> parameter with `Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead.`
> For these models leave `maxTokens = None` and set `maxCompletionTokens`:
>
> ```scala
> val chatRequestBody = ChatBody(
>   model = ChatCompletionModel.GPT5,
>   messages = bodyMessages,
>   maxCompletionTokens = Some(1000)
> )
> ```
