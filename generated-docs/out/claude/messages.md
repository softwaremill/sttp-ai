# Messages API

The [Messages API](https://docs.anthropic.com/claude/reference/messages_post) is Claude's core request/response endpoint: you send a list of `Message`s (each a list of `ContentBlock`s — text, images, tool results) and receive the assistant's reply. This page covers building `MessageRequest`s: multi-turn conversations, system prompts, images, and the advanced sampling parameters. For sending the request see [basics](basics.md); for tools and structured outputs see [Tool calling](tool-calling.md) and [Structured outputs](structured-outputs.md).

## Basic text conversation

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.8.0

import sttp.ai.claude.models.{ClaudeModel, ContentBlock, Message}
import sttp.ai.claude.requests.MessageRequest

val messages = List(
  Message.user(List(ContentBlock.text("What is the capital of France?"))),
  Message.assistant(List(ContentBlock.text("The capital of France is Paris."))),
  Message.user(List(ContentBlock.text("What about Italy?")))
)

val request = MessageRequest.simple(
  model = ClaudeModel.ClaudeSonnet5.value,
  messages = messages,
  maxTokens = 1000
)
```

## System messages

Unlike OpenAI, Claude uses a separate `system` parameter instead of system role messages:

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.8.0

import sttp.ai.claude.models.{ClaudeModel, ContentBlock, Message}
import sttp.ai.claude.requests.MessageRequest

val request = MessageRequest.withSystem(
  model = ClaudeModel.ClaudeSonnet5.value,
  system = "You are a helpful assistant that always responds in French.",
  messages = List(Message.user(List(ContentBlock.text("Hello!")))),
  maxTokens = 1000
)
```

## Image support

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.8.0

import java.util.Base64
import java.nio.file.{Files, Paths}
import sttp.ai.claude.models.{ClaudeModel, ContentBlock, Message}
import sttp.ai.claude.requests.MessageRequest

// Read and encode image
val imageBytes = Files.readAllBytes(Paths.get("image.jpg"))
val base64Image = Base64.getEncoder.encodeToString(imageBytes)

val messages = List(
  Message.user(List(
    ContentBlock.text("What do you see in this image?"),
    ContentBlock.image(
      mediaType = "image/jpeg",
      data = base64Image
    )
  ))
)

val request = MessageRequest.simple(
  model = ClaudeModel.ClaudeSonnet5.value,
  messages = messages,
  maxTokens = 1000
)
```

## Advanced parameters

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.8.0

import sttp.ai.claude.models.{CacheControl, ClaudeModel, ContentBlock, Message}
import sttp.ai.claude.requests.MessageRequest

val messages = List(Message.user(List(ContentBlock.text("Hello!"))))

val request = MessageRequest(
  model = ClaudeModel.ClaudeSonnet5.value,
  messages = messages,
  maxTokens = 4000,
  temperature = Some(0.7),            // Creativity (0.0 - 1.0)
  topP = Some(0.9),                   // Nucleus sampling
  topK = Some(40),                    // Top-k sampling
  stopSequences = Some(List("\n\n")), // Stop generation at sequences
  system = Some("Be concise and helpful."),
  tools = None,                       // See tool-calling.md
  cacheControl = Some(CacheControl.Ephemeral()) // Optional cache control
)
```

## Usage and cache accounting

Regarding caching and usage, note the formula used to calculate the number of input tokens consumed by the model (relevant for billing and context window management):

```scala
case class Usage(
    inputTokens: Int,
    outputTokens: Int,
    cacheReadInputTokens: Option[Int] = None,
    cacheCreationInputTokens: Option[Int] = None
) {
  def totalInputTokens: Int = inputTokens + cacheReadInputTokens.getOrElse(0) + cacheCreationInputTokens.getOrElse(0)
  def totalTokens: Int = totalInputTokens + outputTokens
}
```
