package sttp.ai.claude.models

import sttp.ai.core.json.SnakePickle
import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}
import ujson.Value

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

@upickle.implicits.serializeDefaults(true)
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

  implicit val rw: ReadWriter[UserLocation] = macroRW
}

object Tool {
  case class Custom(
      name: String,
      description: String,
      inputSchema: ToolInputSchema
  ) extends Tool

  @upickle.implicits.key("web_search_20250305")
  case class WebSearch(
      maxUses: Option[Int] = None,
      allowedDomains: Option[List[String]] = None,
      blockedDomains: Option[List[String]] = None,
      userLocation: Option[UserLocation] = None
  ) extends Tool

  object WebSearch {
    final val ToolType = "web_search_20250305"
    final val ToolName = "web_search"

    val default: WebSearch = WebSearch()
  }

  def apply(name: String, description: String, inputSchema: ToolInputSchema): Custom =
    Custom(name, description, inputSchema)

  // manual rw so custom JSON has no `type` field, matching Anthropic documented format
  private val customRW: ReadWriter[Custom] = SnakePickle
    .readwriter[Value]
    .bimap[Custom](
      c =>
        ujson.Obj(
          "name" -> ujson.Str(c.name),
          "description" -> ujson.Str(c.description),
          "input_schema" -> SnakePickle.writeJs(c.inputSchema)
        ),
      json =>
        Custom(
          name = json("name").str,
          description = json("description").str,
          inputSchema = SnakePickle.read[ToolInputSchema](json("input_schema"))
        )
    )

  private val webSearchRW: ReadWriter[WebSearch] = macroRW

  private def withName(json: Value, toolName: String): Value = {
    val obj = scala.collection.mutable.LinkedHashMap[String, Value]()
    json.obj.foreach { case (k, v) =>
      obj.update(k, v)
      if (k == SnakePickle.tagName) obj.update("name", ujson.Str(toolName))
    }
    ujson.Obj.from(obj)
  }

  implicit val toolRW: ReadWriter[Tool] = SnakePickle
    .readwriter[Value]
    .bimap[Tool](
      {
        case c: Custom     => SnakePickle.writeJs(c)(customRW)
        case ws: WebSearch => withName(SnakePickle.writeJs(ws)(webSearchRW), WebSearch.ToolName)
      },
      json =>
        json.obj.get(SnakePickle.tagName).map(_.str) match {
          case Some(WebSearch.ToolType) => SnakePickle.read[WebSearch](json)(webSearchRW)
          case _                        => SnakePickle.read[Custom](json)(customRW)
        }
    )
}
