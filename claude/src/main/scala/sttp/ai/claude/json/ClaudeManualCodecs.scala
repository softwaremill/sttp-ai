package sttp.ai.claude.json

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.syntax._
import sttp.apispec.Schema
import sttp.apispec.circe._
import sttp.ai.claude.models._
import sttp.ai.claude.models.ContentBlock._
import ClaudeDerivedCodecs._

object ClaudeManualCodecs {

  // Claude's structured-output API requires `additionalProperties: false` on every object schema.
  private def addAdditionalPropertiesFalse(json: Json): Json =
    json.arrayOrObject(
      json,
      arr => Json.fromValues(arr.map(addAdditionalPropertiesFalse)),
      obj => {
        val isObjectType = obj("type").contains(Json.fromString("object"))
        val hasProperties = obj.contains("properties")
        val hasAdditionalProperties = obj.contains("additionalProperties")
        val recursed = JsonObject.fromIterable(obj.toIterable.map { case (k, v) => k -> addAdditionalPropertiesFalse(v) })
        val updated =
          if (isObjectType && hasProperties && !hasAdditionalProperties) recursed.add("additionalProperties", Json.False)
          else recursed
        Json.fromJsonObject(updated)
      }
    )

  implicit val outputFormatCodec: Codec[OutputFormat] = Codec.from(
    Decoder.instance(c =>
      c.get[String]("type").flatMap {
        case "text"        => Right(OutputFormat.Text)
        case "json_object" => Right(OutputFormat.JsonObject)
        case "json_schema" => c.downField("schema").as[Schema].map(OutputFormat.JsonSchema.apply)
        case other         => Left(DecodingFailure(s"Unknown OutputFormat type: $other", c.history))
      }
    ),
    Encoder.instance {
      case OutputFormat.Text               => Json.obj("type" := "text")
      case OutputFormat.JsonObject         => Json.obj("type" := "json_object")
      case OutputFormat.JsonSchema(schema) =>
        Json.obj(
          "type" := "json_schema",
          "schema" -> addAdditionalPropertiesFalse(schema.asJson.deepDropNullValues)
        )
    }
  )

  implicit val webSearchToolResultCodec: Codec[WebSearchToolResultBlock] = Codec.from(
    Decoder.instance { c =>
      c.value.asArray match {
        case Some(_) => c.as[List[WebSearchResult]].map(WebSearchToolResultBlock.Results.apply)
        case None    =>
          c.get[String]("type").flatMap {
            case WebSearchToolResultBlock.ErrorTypeValue => c.get[String]("error_code").map(WebSearchToolResultBlock.Error.apply)
            case other => Left(DecodingFailure(s"Unexpected web_search_tool_result content type: $other", c.history))
          }
      }
    },
    Encoder.instance {
      case WebSearchToolResultBlock.Results(items) => items.asJson
      case WebSearchToolResultBlock.Error(code)    =>
        Json.obj("type" := WebSearchToolResultBlock.ErrorTypeValue, "error_code" := code)
    }
  )

  implicit val toolCodec: Codec[Tool] = Codec.from(
    Decoder.instance(c =>
      c.get[String]("type").toOption match {
        case Some(Tool.WebSearch.ToolType) => c.as[Tool.WebSearch]
        case _                             => c.as[Tool.Custom]
      }
    ),
    Encoder.instance {
      case x: Tool.Custom =>
        Json
          .obj(
            "name" := x.name,
            "description" := x.description,
            "input_schema" := x.inputSchema
          )
          .mapObject { obj =>
            x.cacheControl match {
              case Some(cc) => obj.add("cache_control", cc.asJson)
              case None     => obj
            }
          }
      case x: Tool.CustomRaw =>
        Json
          .obj(
            "name" := x.name,
            "description" := x.description,
            "input_schema" := x.inputSchema
          )
          .mapObject { obj =>
            x.cacheControl match {
              case Some(cc) => obj.add("cache_control", cc.asJson)
              case None     => obj
            }
          }
      case x: Tool.WebSearch =>
        x.asJson
          .mapObject(_.add("type", Json.fromString(Tool.WebSearch.ToolType)).add("name", Json.fromString(Tool.WebSearch.ToolName)))
    }
  )
}
