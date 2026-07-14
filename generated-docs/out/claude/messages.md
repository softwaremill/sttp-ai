# Messages API

## Basic Text Conversation

```scala
val messages = List(
  Message.user(List(ContentBlock.text("What is the capital of France?"))),
  Message.assistant(List(ContentBlock.text("The capital of France is Paris."))),
  Message.user(List(ContentBlock.text("What about Italy?")))
)

val request = MessageRequest.simple(
  model = "claude-3-sonnet-20240229",
  messages = messages,
  maxTokens = 1000
)
```

## System Messages

Unlike OpenAI, Claude uses a separate `system` parameter instead of system role messages:

```scala
val request = MessageRequest.withSystem(
  model = "claude-3-sonnet-20240229",
  system = "You are a helpful assistant that always responds in French.",
  messages = List(Message.user(List(ContentBlock.text("Hello!")))),
  maxTokens = 1000
)
```

## Image Support

```scala
import java.util.Base64
import java.nio.file.{Files, Paths}

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
  model = "claude-3-sonnet-20240229",
  messages = messages,
  maxTokens = 1000
)
```

## Advanced Parameters

```scala
import sttp.ai.claude.models.CacheControl

val request = MessageRequest(
  model = "claude-3-sonnet-20240229",
  messages = messages,
  maxTokens = 4000,
  temperature = Some(0.7),           // Creativity (0.0 - 1.0)
  topP = Some(0.9),                  // Nucleus sampling
  topK = Some(40),                   // Top-k sampling
  stopSequences = Some(List("\n\n")), // Stop generation at sequences
  system = Some("Be concise and helpful."),
  tools = Some(tools),                // Tool calling support
  cacheControl = Some(CacheControl.Ephemeral())  // Optional cache control
)
```

Regarding caching and usage, it is important to highlight model and formula used to calculate the number of input tokens 
consumed by the model (relevant for billing and context window management): 

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
This is a breaking change compared to old version of this library (which ignored cache tokens).

