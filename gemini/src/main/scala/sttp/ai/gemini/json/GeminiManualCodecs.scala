package sttp.ai.gemini.json

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
import io.circe.syntax._
import sttp.ai.gemini.models._

object GeminiManualCodecs {

  implicit val interactionStatusCodec: Codec[InteractionStatus] = Codec.from(
    Decoder.decodeString.map(InteractionStatus.fromString),
    Encoder.encodeString.contramap(_.value)
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

  implicit val responseFormatCodec: Codec[ResponseFormat] = Codec.from(
    Decoder.instance(c =>
      c.get[String]("type").flatMap {
        case "text"        => Right(ResponseFormat.Text)
        case "json_schema" =>
          val js = c.downField("json_schema")
          for {
            name <- js.get[String]("name")
            schema <- js.get[Json]("schema")
            description <- js.get[Option[String]]("description")
            strict <- js.get[Option[Boolean]]("strict")
          } yield ResponseFormat.JsonSchema(name, schema, description, strict)
        case other => Left(DecodingFailure(s"Unknown ResponseFormat type: $other", c.history))
      }
    ),
    Encoder.instance {
      case ResponseFormat.Text                                          => Json.obj("type" := "text")
      case ResponseFormat.JsonSchema(name, schema, description, strict) =>
        val inner = Json
          .obj("name" := name, "schema" -> schema)
          .mapObject(obj => description.fold(obj)(d => obj.add("description", Json.fromString(d))))
          .mapObject(obj => strict.fold(obj)(s => obj.add("strict", Json.fromBoolean(s))))
        Json.obj("type" := "json_schema", "json_schema" -> inner)
    }
  )

  implicit val interactionInputCodec: Codec[InteractionInput] = Codec.from(
    Decoder.instance { c =>
      c.value.asString match {
        case Some(text) => Right(InteractionInput.TextInput(text))
        case None       => c.as[List[Step]](Decoder.decodeList(GeminiDerivedCodecs.stepCodec)).map(InteractionInput.StepsInput.apply)
      }
    },
    Encoder.instance {
      case InteractionInput.TextInput(text)   => Json.fromString(text)
      case InteractionInput.StepsInput(steps) => Json.fromValues(steps.map(_.asJson(GeminiDerivedCodecs.stepCodec)))
    }
  )
}
