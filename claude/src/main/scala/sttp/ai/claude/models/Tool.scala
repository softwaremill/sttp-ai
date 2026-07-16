package sttp.ai.claude.models

sealed trait Tool

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
}

case class UserLocation(
    `type`: String = UserLocation.ApproximateType,
    city: Option[String] = None,
    region: Option[String] = None,
    country: Option[String] = None,
    timezone: Option[String] = None
)

object UserLocation {
  val ApproximateType = "approximate"

  def approximate(
      city: Option[String] = None,
      region: Option[String] = None,
      country: Option[String] = None,
      timezone: Option[String] = None
  ): UserLocation = UserLocation(ApproximateType, city, region, country, timezone)
}

object Tool {
  case class Custom(
      name: String,
      description: String,
      inputSchema: ToolInputSchema,
      cacheControl: Option[CacheControl] = None
  ) extends Tool

  /** A tool whose input schema is supplied as raw JSON Schema, passed to the API verbatim. Use this when the schema has structure that
    * [[ToolInputSchema]] cannot express (nested objects, array item types, etc.) — e.g. schemas loaded from MCP servers.
    */
  case class CustomRaw(
      name: String,
      description: String,
      inputSchema: io.circe.Json,
      cacheControl: Option[CacheControl] = None
  ) extends Tool

  case class WebSearch(
      maxUses: Option[Int] = None,
      allowedDomains: Option[List[String]] = None,
      blockedDomains: Option[List[String]] = None,
      userLocation: Option[UserLocation] = None,
      cacheControl: Option[CacheControl] = None
  ) extends Tool

  object WebSearch {
    final val ToolType = "web_search_20250305"
    final val ToolName = "web_search"

    val default: WebSearch = WebSearch()
  }

  def custom(name: String, description: String, inputSchema: io.circe.Json): CustomRaw =
    CustomRaw(name, description, inputSchema)

  def apply(name: String, description: String, inputSchema: ToolInputSchema): Custom =
    Custom(name, description, inputSchema)
}
