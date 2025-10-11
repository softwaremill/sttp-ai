//> using repository ivy2Local
//> using scala 3
//> using dep com.softwaremill.sttp.ai::claude:0.4.0
//> using dep ch.qos.logback:logback-classic:1.5.19
//> using dep com.softwaremill.sttp.client4::upickle:4.0.12

// remember to set the ANTHROPIC_API_KEY env variable!
// run with: ANTHROPIC_API_KEY=... scala-cli run ClaudeToolCallingExample.scala

package examples

import sttp.ai.claude.*
import sttp.ai.claude.ClaudeExceptions.ClaudeException
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.*
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.MessageResponse
import sttp.client4.{DefaultSyncBackend, SyncBackend}

object ClaudeToolCallingExample extends App {

  val config = ClaudeConfig.fromEnv
  val backend: SyncBackend = DefaultSyncBackend()
  val client = ClaudeClient(config)

  // Define tools for Claude to use
  val weatherTool = Tool(
    name = "get_weather",
    description = "Get current weather information for a specific location",
    inputSchema = ToolInputSchema.forObject(
      properties = Map(
        "location" -> PropertySchema.string("The city name or location"),
        "unit" -> PropertySchema.stringEnum("Temperature unit", List("celsius", "fahrenheit"))
      ),
      required = Some(List("location"))
    )
  )

  val calculatorTool = Tool(
    name = "calculate",
    description = "Perform basic mathematical calculations",
    inputSchema = ToolInputSchema.forObject(
      properties = Map(
        "operation" -> PropertySchema.stringEnum("The mathematical operation to perform", List("add", "subtract", "multiply", "divide")),
        "a" -> PropertySchema("number", Some("First number")),
        "b" -> PropertySchema("number", Some("Second number"))
      ),
      required = Some(List("operation", "a", "b"))
    )
  )

  val tools = List(weatherTool, calculatorTool)

  println("=== Claude Tool Calling Example ===")

  val messages = List(
    Message.user("What's the weather like in Paris? Also, what's 15 multiplied by 23?")
  )

  val request = MessageRequest.withTools(
    model = "claude-3-haiku-20240307", // Use a more capable model for tool calling
    messages = messages,
    maxTokens = 1000,
    tools = tools
  )

  val response = client.createMessage(request).send(backend)

  response.body match {
    case Right(messageResponse) =>
      println("Claude's response:")
      messageResponse.content.foreach {
        case ContentBlock.TextContent(text) =>
          println(s"Text: $text")
        case ContentBlock.ToolUseContent(id, name, input) =>
          println(s"Tool called: $name")
          println(s"Tool ID: $id")
          println(s"Tool input: $input")
          // Simulate tool execution
          val toolResult = simulateToolExecution(name, input)
          println(s"Tool result: $toolResult")
        case _ => // Handle other content types if needed
      }

      println(s"\nStop reason: ${messageResponse.stopReason}")
      println(s"Usage: ${messageResponse.usage}")

    case Left(error) =>
      println(s"Error: ${error.getMessage}")
  }

  // Example of tool result handling (would normally involve another API call with tool results)
  println("\n=== Tool Result Follow-up Example ===")

  val toolResultMessages = List(
    Message.user("What's 25 + 17?")
  )

  val toolResultRequest = MessageRequest.withTools(
    model = "claude-3-haiku-20240307",
    messages = toolResultMessages,
    maxTokens = 500,
    tools = List(calculatorTool)
  )

  val toolResultResponse = client.createMessage(toolResultRequest).send(backend)

  toolResultResponse.body match {
    case Right(messageResponse) =>
      println("Claude's tool-assisted calculation:")
      messageResponse.content.foreach {
        case ContentBlock.TextContent(text) =>
          println(text)
        case ContentBlock.ToolUseContent(id, name, input) =>
          val result = simulateToolExecution(name, input)
          println(s"Calculated result: $result")
        case _ => // Handle other content types if needed
      }
    case Left(error) =>
      println(s"Error: ${error.getMessage}")
  }

  backend.close()

  // Simulate tool execution (in a real implementation, these would call actual services)
  private def simulateToolExecution(toolName: String, input: Map[String, ujson.Value]): String =
    toolName match {
      case "get_weather" =>
        val location = input.get("location").map(_.str).getOrElse("Unknown")
        val unit = input.get("unit").map(_.str).getOrElse("celsius")
        s"Weather in $location: 22°${if (unit == "celsius") "C" else "F"}, partly cloudy"

      case "calculate" =>
        val operation = input.get("operation").map(_.str).getOrElse("")
        val a = input.get("a").map(_.num).getOrElse(0.0)
        val b = input.get("b").map(_.num).getOrElse(0.0)

        val result = operation match {
          case "add"      => a + b
          case "subtract" => a - b
          case "multiply" => a * b
          case "divide"   => if (b != 0) a / b else Double.NaN
          case _          => Double.NaN
        }

        s"$a $operation $b = $result"

      case _ =>
        "Tool execution not implemented"
    }
}
