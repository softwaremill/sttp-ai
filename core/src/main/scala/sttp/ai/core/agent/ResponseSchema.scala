package sttp.ai.core.agent

import io.circe.{Codec, DecodingFailure, Encoder, HCursor, Json, JsonObject}
import sttp.apispec.Schema
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TapirSchema}

final case class ResponseSchema[T] private (
    schema: Schema,
    codec: Codec[T],
    description: Option[String]
)

object ResponseSchema {

  def derived[T](
      description: Option[String] = None
  )(implicit ts: TapirSchema[T], codec: Codec[T]): ResponseSchema[T] =
    new ResponseSchema[T](
      schema = TapirSchemaToJsonSchema(ts, markOptionsAsNullable = true),
      codec = codec,
      description = description
    )

  /** Builds a discriminated-union response schema from explicitly listed variants. Works on Scala 2.13 and 3; `U` is typically a sealed
    * trait (on Scala 3, [[UnionResponseSchema.derive]] assembles the variants from a union type and delegates here).
    *
    * Wire shape (uniform across providers; OpenAI strict mode forbids anyOf at the schema root, hence the wrapper object): a root object
    * with a single required `result` property whose schema is the anyOf of the variants, each variant carrying a required
    * `kind: {"enum": ["<name>"]}` discriminator. The model answers e.g. `{"result": {"kind": "Refund", "orderId": "o-1"}}`.
    */
  def oneOf[U](first: Variant[_ <: U], rest: Variant[_ <: U]*): ResponseSchema[U] =
    oneOfImpl(first +: rest, None)

  def oneOf[U](description: String)(first: Variant[_ <: U], rest: Variant[_ <: U]*): ResponseSchema[U] =
    oneOfImpl(first +: rest, Some(description))

  private def oneOfImpl[U](variants: Seq[Variant[_ <: U]], description: Option[String]): ResponseSchema[U] = {
    val duplicateNames = variants.groupBy(_.name).collect { case (n, vs) if vs.size > 1 => n }
    require(duplicateNames.isEmpty, s"duplicate variant names: ${duplicateNames.mkString(", ")}")
    val duplicateClasses = variants.groupBy(_.runtimeClass).collect { case (c, vs) if vs.size > 1 => c.getName }
    require(duplicateClasses.isEmpty, s"duplicate variant classes: ${duplicateClasses.mkString(", ")}")

    val prepared: Seq[(JsonObject, Map[String, Json])] = variants.map { v =>
      val rendered = sttp.apispec.circe
        .encoderSchema(TapirSchemaToJsonSchema(v.tapirSchema, markOptionsAsNullable = true))
        .deepDropNullValues
      val obj = rendered.asObject.getOrElse(
        throw new IllegalArgumentException(s"variant '${v.name}': variant schemas must be object schemas (case classes)")
      )
      require(
        obj("type").contains(Json.fromString("object")),
        s"variant '${v.name}': variant schemas must be object schemas (case classes), got ${rendered.noSpaces}"
      )
      val props = obj("properties").flatMap(_.asObject).getOrElse(JsonObject.empty)
      require(!props.contains("kind"), s"variant '${v.name}' already defines a 'kind' property, which is reserved for the discriminator")
      val nestedDefs = obj("$defs").flatMap(_.asObject).map(_.toMap).getOrElse(Map.empty[String, Json])
      val required = obj("required").flatMap(_.asArray).getOrElse(Vector.empty)
      val withKind = obj
        .remove("$defs")
        .remove("$schema")
        .add("properties", Json.fromJsonObject(props.add("kind", Json.obj("enum" -> Json.arr(Json.fromString(v.name))))))
        .add("required", Json.fromValues(Json.fromString("kind") +: required))
      (withKind, nestedDefs)
    }

    val mergedDefs = prepared.foldLeft(Map.empty[String, Json]) { case (acc, (_, defs)) =>
      defs.foreach { case (key, definition) =>
        acc.get(key).foreach { existing =>
          require(
            existing == definition,
            s"conflicting $$defs entry '$key': two variants nest different schemas under the same name; rename one of the nested types"
          )
        }
      }
      acc ++ defs
    }

    val root = JsonObject(
      "type" -> Json.fromString("object"),
      "required" -> Json.arr(Json.fromString("result")),
      "properties" -> Json.obj("result" -> Json.obj("anyOf" -> Json.fromValues(prepared.map(p => Json.fromJsonObject(p._1)))))
    )
    val rootWithDefs = if (mergedDefs.isEmpty) root else root.add("$defs", Json.fromJsonObject(JsonObject.fromMap(mergedDefs)))
    val apispecSchema: Schema = Json
      .fromJsonObject(rootWithDefs)
      .as[Schema](sttp.apispec.circe.schemaDecoder)
      .fold(e => throw new IllegalArgumentException(s"internal error: assembled union schema failed to parse: ${e.getMessage}"), identity)

    val byName: Map[String, Variant[_ <: U]] = variants.map(v => v.name -> v).toMap
    val validKinds = variants.map(_.name).mkString(", ")

    val unionCodec: Codec[U] = new Codec[U] {
      override def apply(c: HCursor): io.circe.Decoder.Result[U] = {
        val result = c.downField("result")
        result.downField("kind").as[String].flatMap { kind =>
          byName.get(kind) match {
            case Some(v) => v.decoder.tryDecode(result)
            case None    => Left(DecodingFailure(s"unknown kind '$kind'; expected one of: $validKinds", result.history))
          }
        }
      }

      override def apply(u: U): Json = {
        val v = variants
          .find(_.runtimeClass.isInstance(u))
          .getOrElse(throw new IllegalArgumentException(s"value of class ${u.getClass.getName} matches no registered variant"))
        val encoded = v.encoder.asInstanceOf[Encoder[Any]](u)
        val obj = encoded.asObject.getOrElse(
          throw new IllegalArgumentException(s"variant '${v.name}': encoder must produce a JSON object, got ${encoded.noSpaces}")
        )
        Json.obj("result" -> Json.fromJsonObject(obj.add("kind", Json.fromString(v.name))))
      }
    }

    new ResponseSchema[U](apispecSchema, unionCodec, description)
  }
}
