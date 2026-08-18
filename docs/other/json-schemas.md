# JSON Schemas: structured outputs & tools

Two features, available for all providers, need a [JSON Schema](https://json-schema.org):

* **Structured outputs** — constraining the model's response to a given shape, then parsing it into a case class ([OpenAI](../openai/structured-outputs.md), [Claude](../claude/structured-outputs.md), [Gemini](../gemini/structured-outputs.md))
* **Tool calling** — describing the parameters of a tool the model may call ([OpenAI](../openai/tool-calling.md), [Claude](../claude/tool-calling.md), [Gemini](../gemini/tool-calling.md)), including [agent-loop tools](../agents/tools.md)

This page covers the ways to produce such a schema, from fully automatic to fully manual. The provider pages show where to plug the schema in.

## What's needed

Most schema-accepting APIs in this library take a `sttp.apispec.Schema` — the JSON Schema model shared with [Tapir](https://tapir.softwaremill.com). A few take raw `io.circe.Json` instead (Gemini's `Tool.Function`, Claude's `Tool.CustomRaw`). In both cases a Tapir `Schema[T]` derived from a case class is the usual source. Parsing the model's response back into `T` additionally needs a [circe](https://circe.github.io/circe/) `Decoder[T]`.

## The easiest way: derive from a case class

In Scala 3, a `derives` clause supplies both the Tapir schema and the circe codec:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

import sttp.tapir.Schema

case class Step(explanation: String, output: String) derives io.circe.Codec.AsObject, Schema
case class MathReasoning(steps: List[Step], finalAnswer: String) derives io.circe.Codec.AsObject, Schema
```

In Scala 2, use `implicit val schema: Schema[MathReasoning] = Schema.derived` (or `import sttp.tapir.generic.auto.*`) together with circe's semi-automatic derivation.

With these instances in scope, the high-level entry points derive and attach the schema automatically — you never build a schema value yourself:

* `createChatCompletionAs[T]` (OpenAI), `createMessageAs[T]` (Claude), `createInteractionAs[T]` (Gemini) — structured outputs
* `Tool.Function.withSchema[T]` (OpenAI) — tool parameter schemas
* `AgentTool.fromFunction` and `deriveResponseSchema[T]` — [agent loop](../agents/tools.md) tool inputs and typed results

## Customising the derived schema

Underneath, the Tapir schema is converted to JSON Schema with [Tapir's `TapirSchemaToJsonSchema`](https://tapir.softwaremill.com/en/latest/docs/json-schema.html). You can run the conversion yourself to inspect or post-process the result:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.tapir::tapir-apispec-docs:1.13.28

import sttp.apispec.{Schema => ASchema}
import sttp.tapir.Schema
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.generic.auto.*

case class Step(explanation: String, output: String)
case class MathReasoning(steps: List[Step], finalAnswer: String)

val tSchema = implicitly[Schema[MathReasoning]]

val jsonSchema: ASchema = TapirSchemaToJsonSchema(
  tSchema,
  markOptionsAsNullable = true
)
```

To adjust what gets derived, customise the Tapir schema itself — field descriptions, encoded names, validators, and more, via annotations or explicit `Schema` instances; see [Tapir's schema documentation](https://tapir.softwaremill.com/en/latest/endpoint/schemas.html). `markOptionsAsNullable = true` renders `Option` fields as nullable in the JSON Schema.

Note: when OpenAI structured outputs run in strict mode, the schema is additionally normalized by this library — see [strict mode and schema normalization](../openai/structured-outputs.md) for the details and caveats.

## Building a schema manually

If you prefer not to use Tapir derivation — or the schema doesn't correspond to any case class — build the `sttp.apispec.Schema` by hand:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

import scala.collection.immutable.ListMap
import sttp.apispec.{Schema, SchemaType}

val jsonSchema: Schema =
  Schema(SchemaType.Object).copy(
    properties = ListMap(
      "steps" -> Schema(SchemaType.Array).copy(items =
        Some(
          Schema(SchemaType.Object).copy(properties =
            ListMap(
              "explanation" -> Schema(SchemaType.String),
              "output" -> Schema(SchemaType.String)
            )
          )
        )
      ),
      "finalAnswer" -> Schema(SchemaType.String)
    ),
    required = List("steps", "finalAnswer")
  )
```

Where raw JSON is expected instead (Gemini's `Tool.Function` parameters, Claude's `Tool.CustomRaw`), build the `io.circe.Json` value directly — see the [Gemini structured-outputs page](../gemini/structured-outputs.md) for an example.

## Union types: structured intent classification

On Scala 3, a response schema can be derived for a union type, so a classifier agent returns one of several
intents and the caller dispatches with an exhaustive `match`:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

import io.circe.Codec
import sttp.ai.core.agent.*
import sttp.tapir.Schema

final case class Refund(orderId: String) derives Codec.AsObject, Schema
final case class Complaint(topic: String) derives Codec.AsObject, Schema
final case class GeneralQuery() derives Codec.AsObject, Schema

val intentSchema: ResponseSchema[Refund | Complaint | GeneralQuery] =
  UnionResponseSchema.derive[Refund | Complaint | GeneralQuery]("Classify the user's intent")
```

The model sees a uniform wire shape that works across OpenAI (including strict mode, which forbids `anyOf` at the
schema root), Claude, and Gemini: a root object with a single required `result` property holding an `anyOf` of the
variants, each variant carrying a required `kind` discriminator pinned to the variant's name:

```json
{"result": {"kind": "Refund", "orderId": "o-1"}}
```

Each union member needs given tapir `Schema`, circe `Encoder`/`Decoder`, and `ClassTag` instances, and must be a
case-class-like object schema. The variant name defaults to the class's simple name; use `Variant.named` with the
explicit API below to customise it.

On Scala 2.13 — or for sealed traits on either version — list the variants explicitly; the wire shape and codec
are identical (instances shown with Scala 3 `derives` syntax; on Scala 2.13 define the same instances with
`deriveCodec` and `Schema.derived` implicit vals):

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

import io.circe.Codec
import sttp.ai.core.agent.*
import sttp.tapir.Schema

sealed trait Intent
final case class Refund(orderId: String) extends Intent derives Codec.AsObject, Schema
final case class Complaint(topic: String) extends Intent derives Codec.AsObject, Schema

val intentSchema: ResponseSchema[Intent] =
  ResponseSchema.oneOf[Intent](Variant.named[Refund]("refund_request"), Variant[Complaint])
```
