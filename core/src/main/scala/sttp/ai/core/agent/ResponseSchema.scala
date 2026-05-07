package sttp.ai.core.agent

import sttp.ai.core.json.SnakePickle
import sttp.apispec.Schema
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TapirSchema}

final case class ResponseSchema[T] private (
    schema: Schema,
    readWriter: SnakePickle.ReadWriter[T],
    description: Option[String]
)

object ResponseSchema {

  def derived[T](
      description: Option[String] = None
  )(implicit ts: TapirSchema[T], rw: SnakePickle.ReadWriter[T]): ResponseSchema[T] =
    new ResponseSchema[T](
      schema = TapirSchemaToJsonSchema(ts, markOptionsAsNullable = true),
      readWriter = rw,
      description = description
    )
}
