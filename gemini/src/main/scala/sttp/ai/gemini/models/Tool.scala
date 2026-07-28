package sttp.ai.gemini.models

import io.circe.Json

sealed trait Tool

object Tool {

  /** A custom function tool. `parameters` is a raw JSON Schema, passed to the API verbatim (never re-encoded or null-stripped), so schemas
    * loaded from MCP servers survive byte-faithful.
    */
  case class Function(
      name: String,
      description: Option[String] = None,
      parameters: Json = Json.obj()
  ) extends Tool

  /** Google-hosted web search tool. */
  case object GoogleSearch extends Tool

  /** Google-hosted code execution tool. */
  case object CodeExecution extends Tool

  def function(name: String, description: String, parameters: Json): Function =
    Function(name, Some(description), parameters)
}
