package sttp.ai.core.agent

import sttp.apispec.Schema
import sttp.ai.core.json.SnakePickle
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TapirSchema}

/** A backend-neutral description of a typed agent response.
  *
  * Holds a JSON [[Schema]] (derived from `T` via Tapir) plus the uPickle [[SnakePickle.ReadWriter]] used to validate the model's output and
  * round-trip the typed payload through the agent loop.
  *
  * Constructed via [[ResponseSchema.derived]].
  *
  * @param schema
  *   JSON schema for `T`.
  * @param readWriter
  *   uPickle ReadWriter for `T`.
  * @param name
  *   Human-readable name of the schema (used by backends that require one, e.g. OpenAI's `response_format.name`).
  * @param description
  *   Optional description shown to the model alongside the schema.
  */
final case class ResponseSchema[T] private (
    schema: Schema,
    readWriter: SnakePickle.ReadWriter[T],
    name: String,
    description: Option[String]
)

object ResponseSchema {

  /** Derives a [[ResponseSchema]] from a Tapir `Schema[T]` and a uPickle `ReadWriter[T]`.
    *
    * `T` must serialize to a top-level JSON object (i.e. a case class), since both OpenAI and Claude require object-typed tool input
    * schemas.
    */
  def derived[T](
      name: String = "response_payload",
      description: Option[String] = None
  )(implicit ts: TapirSchema[T], rw: SnakePickle.ReadWriter[T]): ResponseSchema[T] =
    new ResponseSchema[T](
      schema = TapirSchemaToJsonSchema(ts, markOptionsAsNullable = true),
      readWriter = rw,
      name = name,
      description = description
    )
}
