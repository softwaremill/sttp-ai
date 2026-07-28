# Structured outputs

Gemini's structured output feature lets you force the model's response to conform to a JSON Schema — useful for data extraction, populating a typed model, or any case where you need a guaranteed shape back instead of free-form text.

## Typed responses with `createInteractionAs[T]`

For the shortest path, use `GeminiSyncClient.createInteractionAs[T]` — the JSON Schema is derived from `T` via Tapir and set on the request automatically (unless the request already carries a `ResponseFormat.JsonSchema`), and the model's output text is parsed back into `T` via circe.

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::gemini:@VERSION@

import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.requests.InteractionRequest
import sttp.tapir.Schema

case class Language(name: String, paradigm: String, summary: String) derives io.circe.Codec.AsObject, Schema
case class LanguageList(languages: List[Language]) derives io.circe.Codec.AsObject, Schema

object Main:
  def main(args: Array[String]): Unit =
    val gemini = GeminiSyncClient.fromEnv
    try {
      val request = InteractionRequest.simple(
        model = "gemini-2.5-flash",
        text = "List 10 well-known programming languages. For each, give the dominant paradigm and a one-sentence summary."
      )
      val result: LanguageList = gemini.createInteractionAs[LanguageList](request)
      result.languages.foreach(l => println(s"${l.name} [${l.paradigm}] - ${l.summary}"))
    } finally gemini.close()
```

`T` must have both a `sttp.tapir.Schema[T]` (for schema generation) and a circe `Decoder[T]` (for parsing) — the `derives io.circe.Codec.AsObject, Schema` clause supplies both in Scala 3.

## Manual `ResponseFormat.JsonSchema`

If you'd rather build (or already have) the JSON Schema yourself, set `ResponseFormat.JsonSchema` on the request directly with `withStructuredOutput` and parse the response text yourself:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::gemini:@VERSION@

import io.circe.Json
import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.models.ResponseFormat
import sttp.ai.gemini.requests.InteractionRequest

case class PersonInfo(name: String, age: Int, occupation: String, skills: List[String])

object PersonInfo:
  given io.circe.Decoder[PersonInfo] = io.circe.generic.semiauto.deriveDecoder[PersonInfo]

object ManualSchemaExample:
  def main(args: Array[String]): Unit =
    val gemini = GeminiSyncClient.fromEnv
    try {
      val schema = Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "name" -> Json.obj("type" -> Json.fromString("string")),
          "age" -> Json.obj("type" -> Json.fromString("integer")),
          "occupation" -> Json.obj("type" -> Json.fromString("string")),
          "skills" -> Json.obj("type" -> Json.fromString("array"), "items" -> Json.obj("type" -> Json.fromString("string")))
        ),
        "required" -> Json.arr(Json.fromString("name"), Json.fromString("age"), Json.fromString("occupation"))
      )

      val request = InteractionRequest
        .simple("gemini-2.5-flash", "Extract information about John, a 30-year-old software engineer who knows Python and Scala.")
        .withStructuredOutput(ResponseFormat.JsonSchema(name = "person_info", schema = schema))

      val response = gemini.createInteraction(request)

      println("Structured JSON output:")
      println(response.outputText)

      val personInfo = io.circe.parser.decode[PersonInfo](response.outputText).toTry.get
      println(s"Parsed: ${personInfo.name}, age ${personInfo.age}, ${personInfo.occupation}")
      println(s"Skills: ${personInfo.skills.mkString(", ")}")
    } finally gemini.close()
```

**Notes:**
- `usesStructuredOutput` on `InteractionRequest` reports whether a `ResponseFormat.JsonSchema` is already set — `createInteractionAs[T]` uses it to avoid overwriting a schema you set explicitly.
- `ResponseFormat.JsonSchema` also accepts `description` and `strict` fields; leave them `None` unless the API docs call for them.
- JSON schemas must be valid and follow standard JSON Schema conventions.
