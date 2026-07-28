package sttp.ai.gemini.json

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
import io.circe.syntax._
import sttp.ai.gemini.models._
import sttp.ai.gemini.responses.ErrorDetail

object GeminiManualCodecs {

  implicit val interactionStatusCodec: Codec[InteractionStatus] = Codec.from(
    Decoder.decodeString.map(InteractionStatus.fromString),
    Encoder.encodeString.contramap(_.value)
  )

  /** `code` can be a JSON string or a JSON number on the wire; it is normalized to a String. */
  implicit val errorDetailCodec: Codec[ErrorDetail] = Codec.from(
    Decoder.instance { c =>
      for {
        codeJson <- c.get[Option[Json]]("code")
        message <- c.get[String]("message")
        status <- c.get[Option[String]]("status")
      } yield {
        val code = codeJson.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString)))
        ErrorDetail(code, message, status)
      }
    },
    Encoder.instance { detail =>
      Json
        .obj("message" := detail.message)
        .mapObject(obj => detail.code.fold(obj)(c => obj.add("code", Json.fromString(c))))
        .mapObject(obj => detail.status.fold(obj)(s => obj.add("status", Json.fromString(s))))
    }
  )

  implicit val toolCodec: Codec[Tool] = Codec.from(
    Decoder.instance(c =>
      c.get[String]("type").flatMap {
        case "function" =>
          for {
            name <- c.get[String]("name")
            description <- c.get[Option[String]]("description")
            parameters <- c.getOrElse[Json]("parameters")(Json.obj())
          } yield Tool.Function(name, description, parameters)
        case "google_search"  => Right(Tool.GoogleSearch)
        case "code_execution" => Right(Tool.CodeExecution)
        case other            => Left(DecodingFailure(s"Unknown Tool type: $other", c.history))
      }
    ),
    Encoder.instance {
      case Tool.Function(name, description, parameters) =>
        Json
          .obj("type" := "function", "name" := name, "parameters" -> parameters)
          .mapObject(obj => description.fold(obj)(d => obj.add("description", Json.fromString(d))))
      case Tool.GoogleSearch  => Json.obj("type" := "google_search")
      case Tool.CodeExecution => Json.obj("type" := "code_execution")
    }
  )

  /** `response_format` is either `{"type": "text"}` or a JSON schema object sent verbatim (no `json_schema` envelope). */
  implicit val responseFormatCodec: Codec[ResponseFormat] = Codec.from(
    Decoder.instance(c =>
      c.get[Option[Json]]("type").map(_.flatMap(_.asString)).flatMap {
        case Some("text") => Right(ResponseFormat.Text)
        case _            => c.as[Json].map(ResponseFormat.JsonSchema.apply)
      }
    ),
    Encoder.instance {
      case ResponseFormat.Text               => Json.obj("type" := "text")
      case ResponseFormat.JsonSchema(schema) => schema
    }
  )

}
