package sttp.ai.gemini.models

import io.circe.Json

/** Structured-output configuration. On the wire this is either `{"type": "text"}`, or a JSON schema object sent verbatim (e.g.
  * `{"type": "object", "properties": {...}, "required": [...]}`) — Gemini's Interactions API does not wrap the schema in a `json_schema`
  * envelope.
  */
sealed trait ResponseFormat

object ResponseFormat {
  case object Text extends ResponseFormat

  /** @param schema the JSON schema, sent to the API exactly as given (no wrapping envelope). */
  case class JsonSchema(schema: Json) extends ResponseFormat
}
