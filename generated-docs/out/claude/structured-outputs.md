# Structured outputs

Claude's structured output feature (currently in beta) allows you to enforce that the model's response follows a specific JSON schema. This is useful for getting consistently formatted responses for data extraction, API responses, and structured data processing.

**Model Support:**
- ✅ **Supported models**: Claude 4.1+ models (`claude-sonnet-4-1-20250514`, `claude-opus-4-1-20250514`, etc.)
- ❌ **Legacy models**: Claude 3.x series don't support structured outputs
- ✅ **Forward compatibility**: Unknown/future models default to supported

## Typed responses with `createMessageAs[T]`

For the shortest path, use `ClaudeSyncClient.createMessageAs[T]` — the response schema is derived from `T` via Tapir, set on the request automatically, and the model's response is parsed back into `T` via circe.

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.5.1+17-73bc7345+20260714-1005-SNAPSHOT

import sttp.ai.claude.ClaudeSyncClient
import sttp.ai.claude.models.Message
import sttp.ai.claude.requests.MessageRequest
import sttp.tapir.Schema

case class Language(name: String, paradigm: String, summary: String) derives io.circe.Codec.AsObject, Schema
case class LanguageList(languages: List[Language]) derives io.circe.Codec.AsObject, Schema

object Main:
  def main(args: Array[String]): Unit =
    val claude = ClaudeSyncClient.fromEnv
    try {
      val request = MessageRequest.simple(
        model = "claude-haiku-4-5-20251001",
        messages = List(Message.user(
          "List 10 well-known programming languages. For each, give the dominant paradigm and a one-sentence summary."
        )),
        maxTokens = 1500
      )
      val result: LanguageList = claude.createMessageAs[LanguageList](request)
      result.languages.foreach(l => println(s"${l.name} [${l.paradigm}] — ${l.summary}"))
    } finally claude.close()
```

`T` must have both a `sttp.tapir.Schema[T]` (for schema generation) and a circe `Codec[T]` (for parsing) — the `derives` clause supplies both in Scala 3.

## Basic Structured Output Example

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.5.1+17-73bc7345+20260714-1005-SNAPSHOT
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.7

import sttp.ai.claude.*
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, Message, OutputFormat}
import sttp.ai.claude.requests.MessageRequest
import sttp.apispec.{Schema => ASchema}
import sttp.client4.*
import sttp.tapir.Schema
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.generic.auto.*

object StructuredOutputExample:
  // Define case class with Schema derivation
  case class PersonInfo(
    name: String,
    age: Int,
    occupation: String,
    skills: List[String]
  )

  object PersonInfo:
    given io.circe.Decoder[PersonInfo] = io.circe.generic.semiauto.deriveDecoder[PersonInfo]

  def main(args: Array[String]): Unit =
    val config = ClaudeConfig.fromEnv
    val backend: SyncBackend = DefaultSyncBackend()
    val client = ClaudeClient(config)

    // Generate JSON schema from case class using Tapir
    val tapirSchema = implicitly[Schema[PersonInfo]]
    val jsonSchema: ASchema = TapirSchemaToJsonSchema(tapirSchema, markOptionsAsNullable = true)

    val outputFormat = OutputFormat.JsonSchema(jsonSchema)

    val messages = List(
      Message.user(List(ContentBlock.text(
        "Extract information about John, a 30-year-old software engineer who knows Python and Scala."
      )))
    )

    val request = MessageRequest
      .simple("claude-sonnet-4-5-20250514", messages, 500)
      .withStructuredOutput(outputFormat)

    val response = client.createMessage(request).send(backend)

    response.body match {
      case Right(messageResponse) =>
        messageResponse.content.foreach {
          case ContentBlock.Text(text, _, _) =>
            println("Structured JSON output:")
            println(text)

            // Parse JSON response back to case class
            val personInfo = io.circe.parser.decode[PersonInfo](text).toTry.get
            println(s"Parsed: ${personInfo.name}, age ${personInfo.age}, ${personInfo.occupation}")
            println(s"Skills: ${personInfo.skills.mkString(", ")}")
          case _ => // Handle other content types
        }
      case Left(error) =>
        println(s"Error: ${error.getMessage}")
    }

    backend.close()
```

## Manual Schema Definition

If you prefer not to use Tapir, you can define schemas manually:

```scala
import sttp.apispec.{Schema => ASchema, SchemaType}
import scala.collection.immutable.ListMap

val schema: ASchema = ASchema(SchemaType.Object).copy(
  properties = ListMap(
    "summary" -> ASchema(SchemaType.String),
    "confidence" -> ASchema(SchemaType.Number).copy(minimum = Some(0), maximum = Some(1))
  ),
  required = List("summary", "confidence")
)
val outputFormat = OutputFormat.JsonSchema(schema)
```

**Important Notes:**
- Structured outputs require Claude 4.1+ models (`claude-sonnet-4-1-*`, `claude-opus-4-1-*`, etc.)
- Legacy models will throw `UnsupportedModelForStructuredOutputException`
- The beta feature uses `anthropic-beta: structured-outputs-2025-11-13` header automatically
- Unknown/future models default to supporting structured outputs for forward compatibility
- JSON schemas must be valid and follow standard JSON Schema specifications

