# Structured outputs / JSON Schema

[OpenAI's Structured Outputs](https://platform.openai.com/docs/guides/structured-outputs/introduction) constrain the model to produce JSON matching a given JSON Schema. The simplest way to use them is `OpenAISyncClient.createChatCompletionAs[T]` — the response schema is derived from a Scala case class via Tapir, set as `responseFormat` automatically, and the model's response is parsed back into `T` via circe:

A structured output request needs a JSON Schema. The easiest way is to have it derived from a case class — see [JSON Schemas: structured outputs & tools](../other/json-schemas.md) for all the options, from automatic derivation to hand-built schemas.

## Typed responses with createChatCompletionAs[T]

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

## Strict mode and schema normalization

normalization is applied only when `strict = true` is requested; otherwise the schema is encoded
faithfully, unchanged. When a schema *is* normalized for strict mode, `additionalProperties: false` is set on every
object, all properties are listed as `required`, and properties that were optional in the source schema (absent
from its original `required` list) are made nullable — the model returns `null` for them instead of inventing a
value. If you decode structured outputs into classes with non-`Option` fields, mark optional fields as `Option` or
list them as required in your schema.

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
