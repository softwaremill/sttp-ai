package sttp.ai.gemini.models

import io.circe.Json

/** Structured-output configuration: `{"type": "json_schema", "json_schema": {...}}` or `{"type": "text"}` on the wire. */
sealed trait ResponseFormat

object ResponseFormat {
  case object Text extends ResponseFormat

  case class JsonSchema(
      name: String,
      schema: Json,
      description: Option[String] = None,
      strict: Option[Boolean] = None
  ) extends ResponseFormat
}
