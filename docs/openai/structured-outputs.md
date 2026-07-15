# Structured outputs / JSON Schema

[OpenAI's Structured Outputs](https://platform.openai.com/docs/guides/structured-outputs/introduction) constrain the model to produce JSON matching a given JSON Schema. The simplest way to use them is `OpenAISyncClient.createChatCompletionAs[T]` — the response schema is derived from a Scala case class via Tapir, set as `responseFormat` automatically, and the model's response is parsed back into `T` via circe:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*
import sttp.tapir.Schema

case class Step(explanation: String, output: String) derives io.circe.Codec.AsObject, Schema
case class MathReasoning(steps: List[Step], finalAnswer: String) derives io.circe.Codec.AsObject, Schema

object Main:
  def main(args: Array[String]): Unit =
    val openAI = OpenAISyncClient(System.getenv("OPENAI_KEY"))
    val chatBody = ChatBody(
      model = ChatCompletionModel.GPT4oMini,
      messages = Seq(
        Message.System("You are a helpful math tutor. Guide the user through the solution step by step."),
        Message.User(Content.TextContent("How can I solve 8x + 7 = -23?"))
      )
    )
    val result: MathReasoning = openAI.createChatCompletionAs[MathReasoning](chatBody)
    println(result.finalAnswer)
    result.steps.foreach(s => println(s"  ${s.explanation} -> ${s.output}"))
```

`T` must have both a `sttp.tapir.Schema[T]` (for schema generation) and a circe `Codec[T]` (for parsing). For custom parsing, the parser-based `createChatCompletion[T](body, name)(parseFunction)` overload remains available.

## Lower-level: building `ResponseFormat.JsonSchema` yourself

If you need finer control — a hand-built schema, custom parsing, or a non-Tapir schema source — use `ResponseFormat.JsonSchema` directly. The example below produces a JSON object:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

import scala.collection.immutable.ListMap
import sttp.apispec.{Schema, SchemaType}
import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel, ResponseFormat}
import sttp.ai.openai.requests.completions.chat.message.*

object Main:
  def main(args: Array[String]): Unit =
    val apiKey = System.getenv("OPENAI_KEY")
    val openAI = OpenAISyncClient(apiKey)

    val jsonSchema: Schema =
      Schema(SchemaType.Object).copy(properties =
        ListMap(
          "steps" -> Schema(SchemaType.Array).copy(items =
            Some(Schema(SchemaType.Object).copy(properties =
              ListMap(
                "explanation" -> Schema(SchemaType.String),
                "output" -> Schema(SchemaType.String)
              )
            ))
          ),
          "finalAnswer" -> Schema(SchemaType.String)
        ),
      )

    val responseFormat: ResponseFormat.JsonSchema =
      ResponseFormat.JsonSchema(
        name = "mathReasoning",
        strict = Some(true),
        schema = Some(jsonSchema),
        description = None
      )

    val bodyMessages: Seq[Message] = Seq(
      Message.System(content = "You are a helpful math tutor. Guide the user through the solution step by step."),
      Message.User(content = Content.TextContent("How can I solve 8x + 7 = -23"))
    )

    // Create body of Chat Completions Request, using our JSON Schema as the `responseFormat`
    val chatRequestBody: ChatBody = ChatBody(
      model = ChatCompletionModel.GPT4oMini,
      messages = bodyMessages,
      responseFormat = Some(responseFormat)
    )

    val chatResponse: ChatResponse = openAI.createChatCompletion(chatRequestBody)

    println(chatResponse.choices)
  /*
    List(
      Choices(
        Message(
          Assistant,
          {
            "steps": [
              {"explanation": "Start with the original equation: 8x + 7 = -23", "output": "8x + 7 = -23"},
              {"explanation": "Subtract 7 from both sides to isolate the term with x.", "output": "8x + 7 - 7 = -23 - 7"},
              {"explanation": "This simplifies to: 8x = -30", "output": "8x = -30"},
              {"explanation": "Now, divide both sides by 8 to solve for x.", "output": "x = -30 / 8"},
              {"explanation": "Simplify -30 / 8 to its simplest form. Both the numerator and denominator can be divided by 2.", "output": "x = -15 / 4"}
            ],
            "finalAnswer": "x = -15/4"
          },
          List(),
          None
        ),
        stop,
        0
      )
    )
  */
```

## Deriving a JSON Schema with tapir

To derive the same math reasoning schema used above, you can use
[Tapir's support for generating a JSON schema from a Tapir schema](https://tapir.softwaremill.com/en/latest/docs/json-schema.html):

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.tapir::tapir-apispec-docs:1.11.7

import sttp.apispec.{Schema => ASchema}
import sttp.tapir.Schema
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.generic.auto.*

case class Step(
  explanation: String,
  output: String
)

case class MathReasoning(
  steps: List[Step],
  finalAnswer: String
)

val tSchema = implicitly[Schema[MathReasoning]]

val jsonSchema: ASchema = TapirSchemaToJsonSchema(
  tSchema,
  markOptionsAsNullable = true
)
```

## Generating JSON Schema from case class

We can also generate JSON Schema directly from case class, without defining the schema manually.

In the example below I define such use case. User tries to book a flight, using function tool. The flow looks as follows:
- User sends a message with the request to book a flight and provides function tool, which means that there is a function on a client side which 'knows' how to book a flight. Within this call it is necessary to provide Json Schema to define function arguments.
- Assistant sends a message with arguments created based on Json Schema provided in the first step.
- User calls custom function with arguments sent by Assistant before.
- User sends result from the function call to Assistant.
- Assistant sends a final result to User.

The key point here is using `Tool.Function.withSchema[T]` method. With this method, Json Schema can be automatically generated using TapirSchemaToJsonSchema functionality. All we need to do is to define case class with [Tapir Schema](https://tapir.softwaremill.com/en/latest/endpoint/schemas.html) defined for it.

Another helpful feature is adding possibility to create `Message.Tool` object passing object instead of String, which will be automatically serialized to Json. All you have to do is just define a circe `Encoder` for the specific class.

With all this in mind please remember that it is still required to deserialized arguments, which are sent back by Assistant to call our function.

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

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
