package sttp.ai.core.agent

import io.circe.Codec
import sttp.apispec.Schema

/** Scala 3-only additions to the [[ResponseSchema]] companion live in the Scala 3 counterpart of this trait — on Scala 2, for discriminated
  * unions, list the variants explicitly with `ResponseSchema.oneOf`.
  *
  * The public `apply` keeps the Scala 2 API of the case class with a private constructor, as under `-Xsource:3` it would be private.
  */
trait ResponseSchemaCompanionVersionSpecific {
  def apply[T](schema: Schema, codec: Codec[T], description: Option[String]): ResponseSchema[T] =
    ResponseSchema.create(schema, codec, description)
}
