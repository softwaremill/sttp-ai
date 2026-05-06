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

case class Citations(enabled: Boolean)

object Citations {
  implicit val rw: ReadWriter[Citations] = macroRW
}

object Tool {
  case class Custom(
      name: String,
      description: String,
      inputSchema: ToolInputSchema
  ) extends Tool

  case class WebSearch(
      maxUses: Option[Int] = None,
      allowedDomains: Option[List[String]] = None,
      blockedDomains: Option[List[String]] = None,
      userLocation: Option[UserLocation] = None
  ) extends Tool

  object WebSearch {
    final val ToolType = "web_search_20250305"
    final val ToolName = "web_search"
  }

  case class WebFetch(
      maxUses: Option[Int] = None,
      allowedDomains: Option[List[String]] = None,
      blockedDomains: Option[List[String]] = None,
      citations: Option[Citations] = None,
      maxContentTokens: Option[Int] = None
  ) extends Tool

  object WebFetch {
    final val ToolType = "web_fetch_20250910"
    final val ToolName = "web_fetch"
    final val BetaHeader = "web-fetch-2025-09-10"
  }

  def apply(name: String, description: String, inputSchema: ToolInputSchema): Custom =
    Custom(name, description, inputSchema)

  // Per-subtype RWs treat each case class as a standalone (no auto-tag), so we get clean field-only JSON.
  // We then dispatch and inject "type"/"name" ourselves in the trait's RW below.
  private val customStandaloneRW: ReadWriter[Custom] = SnakePickle
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

  private def opt[T: SnakePickle.Writer](key: String, value: Option[T], into: scala.collection.mutable.LinkedHashMap[String, Value]): Unit =
    value.foreach(v => into.update(key, SnakePickle.writeJs(v)))

  private def readOpt[T: SnakePickle.Reader](json: Value, key: String): Option[T] =
    json.obj.get(key).flatMap {
      case ujson.Null => None
      case v          => Some(SnakePickle.read[T](v))
    }

  private val webSearchStandaloneRW: ReadWriter[WebSearch] = SnakePickle
    .readwriter[Value]
    .bimap[WebSearch](
      ws => {
        val obj = scala.collection.mutable.LinkedHashMap[String, Value]()
        opt("max_uses", ws.maxUses, obj)
        opt("allowed_domains", ws.allowedDomains, obj)
        opt("blocked_domains", ws.blockedDomains, obj)
        opt("user_location", ws.userLocation, obj)
        ujson.Obj.from(obj)
      },
      json =>
        WebSearch(
          maxUses = readOpt[Int](json, "max_uses"),
          allowedDomains = readOpt[List[String]](json, "allowed_domains"),
          blockedDomains = readOpt[List[String]](json, "blocked_domains"),
          userLocation = readOpt[UserLocation](json, "user_location")
        )
    )

  private val webFetchStandaloneRW: ReadWriter[WebFetch] = SnakePickle
    .readwriter[Value]
    .bimap[WebFetch](
      wf => {
        val obj = scala.collection.mutable.LinkedHashMap[String, Value]()
        opt("max_uses", wf.maxUses, obj)
        opt("allowed_domains", wf.allowedDomains, obj)
        opt("blocked_domains", wf.blockedDomains, obj)
        opt("citations", wf.citations, obj)
        opt("max_content_tokens", wf.maxContentTokens, obj)
        ujson.Obj.from(obj)
      },
      json =>
        WebFetch(
          maxUses = readOpt[Int](json, "max_uses"),
          allowedDomains = readOpt[List[String]](json, "allowed_domains"),
          blockedDomains = readOpt[List[String]](json, "blocked_domains"),
          citations = readOpt[Citations](json, "citations"),
          maxContentTokens = readOpt[Int](json, "max_content_tokens")
        )
    )

  private val TypeKey = "type"
  private val NameKey = "name"

  private def withTypeAndName(body: Value, toolType: String, toolName: String): Value = {
    val merged = scala.collection.mutable.LinkedHashMap[String, Value]()
    merged.update(TypeKey, ujson.Str(toolType))
    merged.update(NameKey, ujson.Str(toolName))
    body.obj.foreach { case (k, v) => if (k != TypeKey && k != NameKey) merged.update(k, v) }
    ujson.Obj.from(merged)
  }

  implicit val toolRW: ReadWriter[Tool] = SnakePickle
    .readwriter[Value]
    .bimap[Tool](
      {
        case c: Custom     => SnakePickle.writeJs(c)(customStandaloneRW)
        case ws: WebSearch => withTypeAndName(SnakePickle.writeJs(ws)(webSearchStandaloneRW), WebSearch.ToolType, WebSearch.ToolName)
        case wf: WebFetch  => withTypeAndName(SnakePickle.writeJs(wf)(webFetchStandaloneRW), WebFetch.ToolType, WebFetch.ToolName)
      },
      json =>
        json.obj.get(TypeKey).map(_.str) match {
          case Some(WebSearch.ToolType) => SnakePickle.read[WebSearch](json)(webSearchStandaloneRW)
          case Some(WebFetch.ToolType)  => SnakePickle.read[WebFetch](json)(webFetchStandaloneRW)
          case _                        => SnakePickle.read[Custom](json)(customStandaloneRW)
        }
    )
}
