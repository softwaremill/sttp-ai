package sttp.ai.claude.models

import sttp.apispec.Schema
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TSchema}

sealed trait OutputFormat

object OutputFormat {
  case object Text extends OutputFormat
  case object JsonObject extends OutputFormat
  case class JsonSchema(schema: Schema) extends OutputFormat

  object JsonSchema {
    def withTapirSchema[T: TSchema]: JsonSchema = {
      val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
      JsonSchema(schema)
    }
  }
}
