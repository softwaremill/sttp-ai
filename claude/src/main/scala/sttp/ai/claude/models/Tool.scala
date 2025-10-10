package sttp.ai.claude.models

import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}

case class Tool(
    name: String,
    description: String,
    inputSchema: ToolInputSchema
)

case class ToolInputSchema(
    `type`: String,
    properties: Map[String, PropertySchema],
    required: Option[List[String]] = None
)

case class PropertySchema(
    `type`: String,
    description: Option[String] = None,
    `enum`: Option[List[String]] = None
)

object PropertySchema {
  def string(description: String): PropertySchema = PropertySchema(
    `type` = "string",
    description = Some(description)
  )

  def integer(description: String): PropertySchema = PropertySchema(
    `type` = "integer",
    description = Some(description)
  )

  def boolean(description: String): PropertySchema = PropertySchema(
    `type` = "boolean",
    description = Some(description)
  )

  def stringEnum(description: String, values: List[String]): PropertySchema = PropertySchema(
    `type` = "string",
    description = Some(description),
    `enum` = Some(values)
  )

  implicit val rw: ReadWriter[PropertySchema] = macroRW
}

object ToolInputSchema {
  def forObject(
      properties: Map[String, PropertySchema],
      required: Option[List[String]] = None
  ): ToolInputSchema = ToolInputSchema(
    `type` = "object",
    properties = properties,
    required = required
  )

  implicit val rw: ReadWriter[ToolInputSchema] = macroRW
}

object Tool {
  implicit val rw: ReadWriter[Tool] = macroRW
}
