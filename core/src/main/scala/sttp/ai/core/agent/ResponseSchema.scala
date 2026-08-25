package sttp.ai.core.agent

import io.circe.{Codec, DecodingFailure, Encoder, HCursor, Json}
import sttp.apispec.{ExampleSingleValue, Schema, SchemaLike, SchemaType}
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TapirSchema}

import scala.collection.immutable.ListMap

final case class ResponseSchema[T] private (
    schema: Schema,
    codec: Codec[T],
    description: Option[String]
)

object ResponseSchema extends ResponseSchemaCompanionVersionSpecific {

  def derived[T](
      description: Option[String] = None
  )(implicit ts: TapirSchema[T], codec: Codec[T]): ResponseSchema[T] =
    new ResponseSchema[T](
      schema = renderTapirSchema(ts),
      codec = codec,
      description = description
    )

  /** The one rendering convention for turning tapir schemas into apispec JSON schemas across the agent layer: `Option` fields are marked
    * nullable, matching what providers expect for non-required properties.
    */
  private[agent] def renderTapirSchema[A](ts: TapirSchema[A]): Schema =
    TapirSchemaToJsonSchema(ts, markOptionsAsNullable = true)

  /** Builds a discriminated-union response schema from explicitly listed variants. Works on Scala 2.13 and 3; `U` is typically a sealed
    * trait (on Scala 3, `derivedUnion` assembles the variants from a union type and delegates here).
    *
    * Wire shape (uniform across providers; OpenAI strict mode forbids anyOf at the schema root, hence the wrapper object): a root object
    * with a single required `result` property whose schema is the anyOf of the variants, each variant carrying a required
    * `kind: {"type": "string", "enum": ["<name>"]}` discriminator. The model answers e.g. `{"result": {"kind": "Refund", "orderId":
    * "o-1"}}`.
    *
    * Encoding dispatches on the runtime class of the value, first match by declaration order: list subtypes before their supertypes, or a
    * variant whose class is a supertype of a later variant's class will shadow it (not reachable with plain case-class variants, whose
    * classes are unrelated).
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

    // Assembled entirely in the typed apispec model: `properties` is a ListMap, so the discriminator-first invariant
    // below holds structurally (no JSON round trip whose key-order preservation we would silently depend on).
    val prepared: Seq[(Schema, ListMap[String, SchemaLike])] = variants.map { v =>
      val rendered = renderTapirSchema(v.tapirSchema)
      require(
        rendered.$ref.isEmpty,
        s"variant '${v.name}': reference-rooted schemas (e.g. hand-built recursive schemas rendering to a root $$ref) are not " +
          "supported as union variants; use a non-recursive case class, or ResponseSchema.derived for a single recursive type"
      )
      require(
        rendered.`type`.contains(List(SchemaType.Object)),
        s"variant '${v.name}': variant schemas must be object schemas (case classes)"
      )
      require(
        !rendered.properties.contains("kind"),
        s"variant '${v.name}' already defines a 'kind' property, which is reserved for the discriminator"
      )
      // The discriminator must be the FIRST property: structured-output grammars (OpenAI strict mode, Claude) constrain
      // generation to the schema's property order, and models lead with the discriminator - if `kind` were last, emitting
      // it first would eliminate every variant except those whose only property is `kind` (verified live: all non-empty
      // variants became undecodable and the model was forced into the empty variant).
      val kindSchema = Schema(`type` = Some(List(SchemaType.String)), `enum` = Some(List(ExampleSingleValue(v.name))))
      val withKind = rendered.copy(
        $schema = None,
        $defs = None,
        properties = ListMap("kind" -> kindSchema) ++ rendered.properties,
        required = "kind" :: rendered.required
      )
      (withKind, rendered.$defs.getOrElse(ListMap.empty))
    }

    val mergedDefs: ListMap[String, SchemaLike] = prepared.foldLeft(ListMap.empty[String, SchemaLike]) { case (acc, (_, defs)) =>
      defs.foldLeft(acc) { case (a, (key, definition)) =>
        a.get(key) match {
          case Some(existing) =>
            require(
              existing == definition,
              s"conflicting $$defs entry '$key': two variants nest different schemas under the same name; rename one of the nested types"
            )
            a
          case None => a.updated(key, definition)
        }
      }
    }

    val root = Schema(
      $schema = Some("https://json-schema.org/draft/2020-12/schema"),
      $defs = if (mergedDefs.isEmpty) None else Some(mergedDefs),
      // embedded in the schema itself (not only in ResponseSchema.description) so it reaches every provider - the
      // standalone description field is currently forwarded by the OpenAI backend only
      description = description,
      `type` = Some(List(SchemaType.Object)),
      required = List("result"),
      properties = ListMap("result" -> Schema(anyOf = prepared.map(p => p._1: SchemaLike).toList))
    )

    val byName: Map[String, Variant[_ <: U]] = variants.map(v => v.name -> v).toMap
    val validKinds = variants.map(_.name).mkString(", ")

    val unionCodec: Codec[U] = new Codec[U] {
      override def apply(c: HCursor): io.circe.Decoder.Result[U] = {
        val result = c.downField("result")
        result.downField("kind").as[String].flatMap { kind =>
          byName.get(kind) match {
            case Some(v) =>
              // The discriminator is synthetic (injected by oneOf, forbidden as a variant field), so strip it before
              // handing the object to the variant decoder - a decoder that rejects unknown fields must not see it.
              val withoutKind = result.focus.flatMap(_.asObject) match {
                case Some(o) => Json.fromJsonObject(o.remove("kind"))
                case None    => result.focus.getOrElse(Json.Null)
              }
              // re-anchor variant decode failures under the .result path, which decodeJson's fresh cursor loses
              v.decoder.decodeJson(withoutKind).left.map(df => DecodingFailure(df.message, df.history ::: result.history))
            case None => Left(DecodingFailure(s"unknown kind '$kind'; expected one of: $validKinds", result.history))
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
        require(
          !obj.contains("kind"),
          s"variant '${v.name}': encoder emitted a 'kind' field, which is reserved for the discriminator"
        )
        Json.obj("result" -> Json.fromJsonObject(obj.add("kind", Json.fromString(v.name))))
      }
    }

    new ResponseSchema[U](root, unionCodec, description)
  }
}
