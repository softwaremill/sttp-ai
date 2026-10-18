package sttp.ai.core.agent

import io.circe.Codec
import sttp.apispec.Schema

/** The public `copy` keeps the Scala 2 API of the case class with a private constructor, as under `-Xsource:3` it would be private. */
trait ResponseSchemaVersionSpecific[T] { self: ResponseSchema[T] =>
  def copy(schema: Schema = self.schema, codec: Codec[T] = self.codec, description: Option[String] = self.description): ResponseSchema[T] =
    ResponseSchema.create(schema, codec, description)
}
