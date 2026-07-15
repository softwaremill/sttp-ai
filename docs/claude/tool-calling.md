# Tool calling

## Custom Tools

Define your own tools that Claude calls and your application executes:

```scala
import sttp.ai.claude.models.{Tool, ToolInputSchema, PropertySchema}

val weatherTool = Tool(
  name = "get_weather",
  description = "Get current weather for a location",
  inputSchema = ToolInputSchema(
    `type` = "object",
    properties = Map(
      "location" -> PropertySchema(`type` = "string", description = Some("City name")),
      "unit" -> PropertySchema(`type` = "string", `enum` = Some(List("celsius", "fahrenheit")))
    ),
    required = Some(List("location"))
  )
)

val request = MessageRequest.withTools(
  model = "claude-3-sonnet-20240229",
  messages = List(Message.user(List(ContentBlock.text("What's the weather in Paris?")))),
  maxTokens = 1000,
  tools = List(weatherTool)
)
```

## Predefined Tools

Currently supported:

- **`Tool.WebSearch`** (`web_search_20250305`)

```scala
import sttp.ai.claude.models.{ContentBlock, Message, Tool}
import sttp.ai.claude.requests.MessageRequest

val request = MessageRequest.withTools(
  model = "claude-sonnet-4-5-20250514",
  messages = List(Message.user(List(ContentBlock.text("What was the most recent SpaceX launch?")))),
  maxTokens = 1024,
  tools = List(Tool.WebSearch.default)
)

val response = client.createMessage(request)

response.content.foreach {
  case t: ContentBlock.Text              => println(t.text)
  case s: ContentBlock.ServerToolUse     =>
    println(s"Searched for: ${s.input.get("query").map(_.str).getOrElse("")}")
  case r: ContentBlock.WebSearchToolResult =>
    r.content match {
      case ContentBlock.WebSearchToolResultBlock.Results(items) =>
        items.foreach(it => println(s"- ${it.title} — ${it.url}"))
      case ContentBlock.WebSearchToolResultBlock.Error(code) =>
        println(s"Web search failed: $code")
    }
  case _                                        => ()
}
```

Both custom and predefined tools can be passed in the same `tools` list.

