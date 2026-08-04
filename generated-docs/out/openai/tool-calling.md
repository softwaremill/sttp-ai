# Tool calling

Tools (also called function calling) let the model request that your application execute a function and report its result back. Each tool is described to the model by a name, a description, and a JSON Schema for its parameters — see [JSON Schemas: structured outputs & tools](../other/json-schemas.md) for all the ways to produce one.

## Calling a tool with `Tool.Function.withSchema[T]`

The example below books a flight using a function tool. The flow:

- The user sends a message asking to book a flight, together with a function tool definition — meaning there is a function on the client side which knows how to book a flight. The tool definition carries a JSON Schema describing the function's arguments.
- The assistant responds with a tool call: arguments matching that schema.
- The application decodes the arguments, calls the actual function, and sends its result back to the assistant.
- The assistant produces the final answer.

The key point is `Tool.Function.withSchema[T]`: the JSON Schema for the arguments is generated automatically from the case class `T`, which only needs a [Tapir Schema](https://tapir.softwaremill.com/en/latest/endpoint/schemas.html). A related convenience is creating the `Message.Tool` reply from an object rather than a JSON string — it is serialized automatically given a circe `Encoder`.

Note that the arguments sent back by the assistant still need to be deserialized manually before calling the function.

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.6.0

import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatBody
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel.GPT4oMini
import sttp.ai.openai.requests.completions.chat.ToolCall.FunctionToolCall
import sttp.ai.openai.requests.completions.chat.message.Content.TextContent
import sttp.ai.openai.requests.completions.chat.message.Message.{Assistant, Tool, User}
import sttp.ai.openai.requests.completions.chat.message.Tool.Function
import sttp.tapir.generic.auto.*

case class Passenger(name: String, age: Int)

object Passenger:
  given io.circe.Decoder[Passenger] = io.circe.generic.semiauto.deriveDecoder[Passenger]

case class FlightDetails(passenger: Passenger, departureCity: String, destinationCity: String)

object FlightDetails:
  given io.circe.Decoder[FlightDetails] = io.circe.generic.semiauto.deriveDecoder[FlightDetails]

case class BookedFlight(confirmationNumber: String, status: String)

object BookedFlight:
  given io.circe.Encoder[BookedFlight] = io.circe.generic.semiauto.deriveEncoder[BookedFlight]

object Main:
  def main(args: Array[String]): Unit =
    val apiKey = System.getenv("OPENAI_KEY")
    val openAI = OpenAISyncClient(apiKey)

    val initialRequestMessage = Seq(User(content = TextContent("I want to book a flight from London to Tokyo for Jane Doe, age 34")))

    // Request created using Tool.Function.withSchema, all we need to do here is just define the type. The schema is automatically generated using a macro, available via the `sttp.tapir.generic.auto.*` import.
    val givenRequest = ChatBody(
      model = GPT4oMini,
      messages = initialRequestMessage,
      tools = Some(Seq(
        Function.withSchema[FlightDetails](
          name = "book_flight",
          description = Some("Books a flight for a passenger with full details")))
      )
    )

    val initialRequestResult = openAI.createChatCompletion(givenRequest)

    println(initialRequestResult.choices)
    /*
      List(
        Choices(
          Message(
            null,
            None,
            List(
              FunctionToolCall(
                Some(call_XZNvfldLQTa1f7aMInswpTMS),
                FunctionCall(
                  {
                    "passenger": {
                      "name": "Jane Doe",
                      "age": 34
                    },
                    "departureCity": "London",
                    "destinationCity": "Tokyo"
                  },
                  Some(book_flight)
                )
              )
            ),
            Assistant,
            None,
            None
          ),
          tool_calls,
          0,
          None
        )
      )
      */

    // Helper function that mimics external function definition
    def bookFlight(flightDetails: FlightDetails): BookedFlight =
      println(flightDetails)
      BookedFlight(confirmationNumber = "123456", status = "confirmed")

    // Tool calls list (in this example it is just single tool call, but there may be multiple), which is necessary to build message list for second request.
    val toolCalls = initialRequestResult.choices.head.message.toolCalls

    val functionToolCall = toolCalls.head match
      case functionToolCall: FunctionToolCall => functionToolCall

    // Function arguments are manually deserialized, 'bookFlight' function mimic external function definition.
    val bookedFlight = bookFlight(io.circe.parser.decode[FlightDetails](functionToolCall.function.arguments).toTry.get)

    val secondRequest = givenRequest.copy(
      messages = initialRequestMessage
        :+ Assistant(content = "", toolCalls = toolCalls)
        // Tool message created using object instead of String with Json representation of object.
        :+ Tool(toolCallId = functionToolCall.id.get, content = bookedFlight)
    )

    val finalResult = openAI.createChatCompletion(secondRequest)

    println(finalResult.choices)
    /*
      List(
        Choices(
          Message(
            "The flight from London to Tokyo for Jane Doe, age 34, has been successfully booked. The confirmation number is **123456** and the status is **confirmed**.",
            None,
            List(),
            Assistant,
            None,
            None
          ),
          stop,
          0,
          None
        )
      )
      */
```

## Using the agent loop

For a full automatic tool-calling loop — the model calls a tool, your code runs it, the result is fed back, repeat until the model produces a final answer — use `OpenAIAgent` instead of driving `createChatCompletion` by hand: see the [agent loop](../agents/quickstart.md).
