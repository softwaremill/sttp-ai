package sttp.ai.claude.models

import sttp.apispec.Schema
import sttp.ai.core.json.SnakePickle
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TSchema}
import ujson.Value
import io.circe.syntax._
import io.circe.DecodingFailure
import sttp.apispec.circe._
import ujson.circe.CirceJson

sealed trait OutputFormat

object OutputFormat {
  case object Text extends OutputFormat
  case object JsonObject extends OutputFormat
  @upickle.implicits.key("json_schema")
  case class JsonSchema(schema: Schema) extends OutputFormat

  object JsonSchema {
    def withTapirSchema[T: TSchema]: JsonSchema = {
      val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
      JsonSchema(schema)
    }
  }

  private case class ParseException(circeException: DecodingFailure) extends Exception("Failed to parse JSON schema", circeException)

  // Claude API requires `additionalProperties: false` to exist always, without that it throws API exception
  private def addAdditionalPropertiesFalse(value: Value): Value = value match {
    case obj: ujson.Obj =>
      val isObjectType = obj.value.get("type").exists {
        case ujson.Str("object") => true
        case _                   => false
      }
      val hasProperties = obj.value.contains("properties")
      val hasAdditionalProperties = obj.value.contains("additionalProperties")

      if (isObjectType && hasProperties && !hasAdditionalProperties) {
        val updated = obj.value.toMap + ("additionalProperties" -> ujson.Bool(false))
        ujson.Obj.from(updated.view.mapValues(addAdditionalPropertiesFalse))
      } else {
        ujson.Obj.from(obj.value.view.mapValues(addAdditionalPropertiesFalse))
      }
    case arr: ujson.Arr =>
      ujson.Arr.from(arr.value.map(addAdditionalPropertiesFalse))
    case other => other
  }

  implicit private val schemaRW: SnakePickle.ReadWriter[Schema] = SnakePickle
    .readwriter[Value]
    .bimap(
      s => {
        val circeJson = s.asJson.deepDropNullValues
        val ujsonValue = CirceJson.transform(circeJson, upickle.default.reader[Value])
        addAdditionalPropertiesFalse(ujsonValue)
      },
      v =>
        upickle.default.transform(v).to(CirceJson).as[Schema] match {
          case Left(e)  => throw ParseException(e)
          case Right(s) => s
        }
    )

  implicit val jsonSchemaRW: SnakePickle.ReadWriter[JsonSchema] = SnakePickle.macroRW[JsonSchema]

  implicit val outputFormatRW: SnakePickle.ReadWriter[OutputFormat] = SnakePickle
    .readwriter[Value]
    .bimap[OutputFormat](
      {
        case Text       => ujson.Obj("type" -> "text")
        case JsonObject => ujson.Obj("type" -> "json_object")
        case js: JsonSchema =>
          val baseJson = SnakePickle.writeJs(js)
          val objWithType = baseJson.obj.clone()
          objWithType("type") = "json_schema"
          objWithType
      },
      {
        case obj if obj.obj.get("type").contains(ujson.Str("text"))        => Text
        case obj if obj.obj.get("type").contains(ujson.Str("json_object")) => JsonObject
        case obj if obj.obj.get("type").contains(ujson.Str("json_schema")) =>
          SnakePickle.read[JsonSchema](obj)
        case other => throw new Exception(s"Unknown OutputFormat: $other")
      }
    )
}
