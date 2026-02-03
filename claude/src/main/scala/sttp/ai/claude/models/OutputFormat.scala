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

  // JSON Schema field name constants
  private val TypeKey = "type"
  private val PropertiesKey = "properties"
  private val AdditionalPropertiesKey = "additionalProperties"
  private val ObjectTypeValue = "object"

  // OutputFormat type value constants
  private val TextTypeValue = "text"
  private val JsonObjectTypeValue = "json_object"
  private val JsonSchemaTypeValue = "json_schema"

  // Claude API requires `additionalProperties: false` to exist always, without that it throws API exception
  private def addAdditionalPropertiesFalse(value: Value): Value = value match {
    case obj: ujson.Obj =>
      val isObjectType = obj.value.get(TypeKey).contains(ujson.Str(ObjectTypeValue))
      val hasProperties = obj.value.contains(PropertiesKey)
      val hasAdditionalProperties = obj.value.contains(AdditionalPropertiesKey)
      val needsAdditionalProperties = isObjectType && hasProperties && !hasAdditionalProperties

      val updated =
        if (needsAdditionalProperties) obj.value.toMap + (AdditionalPropertiesKey -> ujson.Bool(false))
        else obj.value

      ujson.Obj.from(updated.view.mapValues(addAdditionalPropertiesFalse))
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
        case Text           => ujson.Obj(TypeKey -> TextTypeValue)
        case JsonObject     => ujson.Obj(TypeKey -> JsonObjectTypeValue)
        case js: JsonSchema => SnakePickle.writeJs(js).obj + (TypeKey -> ujson.Str(JsonSchemaTypeValue))
      },
      obj =>
        obj.obj.get(TypeKey) match {
          case Some(ujson.Str(TextTypeValue))       => Text
          case Some(ujson.Str(JsonObjectTypeValue)) => JsonObject
          case Some(ujson.Str(JsonSchemaTypeValue)) => SnakePickle.read[JsonSchema](obj)
          case other                                => throw new Exception(s"Unknown OutputFormat: $other")
        }
    )
}
