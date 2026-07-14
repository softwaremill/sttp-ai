# Models, errors and the sync client

## Claude Models API

```scala
val modelsRequest = client.listModels()
val models = modelsRequest.send(backend)

models match {
  case Right(response) =>
    response.data.foreach(model => println(s"${model.id} - ${model.displayName.getOrElse("N/A")}"))
  case Left(error) =>
    println(s"Error: $error")
}
```

**Common Claude models** (use `listModels()` for current list):

- `claude-3-sonnet-20240229` - Balanced performance and speed
- `claude-3-opus-20240229` - Highest capability model
- `claude-3-haiku-20240307` - Fastest model
- `claude-instant-1.2` - Legacy fast model

## Claude Error Handling

Claude-specific exception hierarchy:

```scala
import sttp.ai.claude.ClaudeExceptions.*

client.createMessage(request).send(backend) match {
  case Right(response) => // Success
    handleResponse(response)
  case Left(error) => error match {
    case _: AuthenticationException => // Invalid API key
      println("Authentication failed - check your API key")
    case _: RateLimitException => // Rate limited
      println("Rate limited - please wait before retrying")
    case _: InvalidRequestException => // Malformed request
      println("Invalid request - check your parameters")
    case _: PermissionException => // Access denied
      println("Permission denied for this resource")
    case _: APIException => // Other API error
      println(s"API error: ${error.getMessage}")
    case _: DeserializationClaudeException => // JSON parsing error
      println("Failed to parse response")
  }
}
```

## Key Differences from OpenAI API

| Feature | Claude API | OpenAI API |
|---------|------------|------------|
| **Message Content** | `ContentBlock` arrays | Simple strings |
| **System Messages** | `system` parameter | Role-based message |
| **Authentication** | `x-api-key` + `anthropic-version` headers | `Authorization` header |
| **Image Input** | ContentBlock with base64 | URL or base64 in content |
| **Tool Calling** | Native tool structure | Function calling |
| **Streaming** | Server-Sent Events | Server-Sent Events |
| **Model Names** | `claude-3-sonnet-20240229` | `gpt-4` |

## Synchronous Claude Client

For blocking operations, use `ClaudeSyncClient`:

```scala
import sttp.ai.claude.ClaudeSyncClient

val syncClient = new ClaudeSyncClient(config)

// Throws ClaudeException on error
try {
  val response = syncClient.createMessage(request)
  println(response.content.head.text.getOrElse(""))
} catch {
  case e: ClaudeException => println(s"Error: ${e.getMessage}")
}
```

