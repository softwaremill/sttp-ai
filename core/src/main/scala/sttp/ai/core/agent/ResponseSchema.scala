package sttp.ai.core.agent

import io.circe.Codec
import sttp.apispec.Schema
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TapirSchema}

final case class ResponseSchema[T] private (
    schema: Schema,
    codec: Codec[T],
    description: Option[String]
)

object ResponseSchema {

  def derived[T](
      description: Option[String] = None
  )(implicit ts: TapirSchema[T], codec: Codec[T]): ResponseSchema[T] =
    new ResponseSchema[T](
      schema = TapirSchemaToJsonSchema(ts, markOptionsAsNullable = true),
      codec = codec,
      description = description
    )
}
