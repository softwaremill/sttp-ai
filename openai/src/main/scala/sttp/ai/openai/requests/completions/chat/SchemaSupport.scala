package sttp.ai.openai.requests.completions.chat

import io.circe.syntax._
import io.circe.{Codec, Encoder, Json, JsonNumber, JsonObject}
import sttp.apispec.Schema
import sttp.apispec.circe.encoderSchema

object SchemaSupport {

  /** circe codec for apispec `Schema` that encodes FAITHFULLY, i.e. it does not rewrite the JSON in any way: it's a plain apispec codec
    * that additionally drops unset-field nulls (via `deepDropNullValues`) on encode. Use this whenever the caller has not asked for OpenAI
    * strict-mode shape (e.g. `strict = None`/`Some(false)`, or a schema factory with no strict concept at all). For strict mode, use
    * [[normalizeForStrict]] instead.
    */
  implicit val schemaCodec: Codec[Schema] = Codec.from(
    sttp.apispec.circe.schemaDecoder,
    Encoder.instance(s => encoderSchema(s).deepDropNullValues)
  )

  /** Normalizes a JSON Schema (already encoded as `Json`) to OpenAI's strict-mode rules: `additionalProperties: false` on every object,
    * every property listed in `required`, and properties that were originally optional made nullable (including adding `null` to `enum`).
    * Only apply this when the caller actually requested strict mode (`strict: true`); everything else should get a faithful encoding (see
    * [[schemaCodec]]).
    */
  def normalizeForStrict(schemaJson: Json): Json = schemaJson.foldWith(schemaFolder)

  /** Normalizes an apispec `Schema` to OpenAI's strict-mode rules. See [[normalizeForStrict(Json)]]. */
  def normalizeForStrict(schema: Schema): Json = normalizeForStrict(encoderSchema(schema).deepDropNullValues)

  private case class FolderState(
      fields: List[(String, Json)],
      addAdditionalProperties: Boolean,
      requiredProperties: List[String]
  )

  /** OpenAI's JSON schema support imposes two requirements:
    *
    *   1. All fields must be `required`: https://platform.openai.com/docs/guides/structured-outputs/all-fields-must-be-required
    *   2. `additionalProperties: false` must always be set in objects:
    *      https://platform.openai.com/docs/guides/structured-outputs/additionalproperties-false-must-always-be-set-in-objects
    *
    * We implement these by folding over the JSON structure. However, if a schema uses discriminated unions (indicated by a `discriminator`
    * property), we skip forcing `additionalProperties: false` to preserve flexibility in selecting sub-schemas.
    */
  private val schemaFolder: Json.Folder[Json] = new Json.Folder[Json] {
    lazy val onNull: Json = Json.Null
    def onBoolean(value: Boolean): Json = Json.fromBoolean(value)
    def onNumber(value: JsonNumber): Json = Json.fromJsonNumber(value)
    def onString(value: String): Json = Json.fromString(value)
    def onArray(value: Vector[Json]): Json = Json.fromValues(value.map(_.foldWith(this)))
    def onObject(value: JsonObject): Json = {
      val originalRequired: Set[String] = value("required")
        .flatMap(_.asArray)
        .map(_.flatMap(_.asString).toSet)
        .getOrElse(Set.empty)

      val state = value.toList.foldRight(FolderState(Nil, addAdditionalProperties = false, Nil)) { case ((k, v), acc) =>
        if (k == "properties") {
          // The container map's own entries are parameter NAMES (which may themselves be "properties", "type", "required", ...), not
          // schema keywords: fold each entry's schema VALUE, but never fold the container object itself through `onObject` (F6 - doing so
          // would treat a parameter literally named "properties"/"type"/"required" as if it were a schema keyword of the container).
          val foldedProps = v.asObject match {
            case Some(props) => Json.fromJsonObject(JsonObject.fromIterable(props.toList.map { case (n, s) => n -> s.foldWith(this) }))
            case None        => v.foldWith(this)
          }
          val nullableProps = foldedProps.asObject match {
            case Some(propsObj) =>
              Json.fromJsonObject(JsonObject.fromIterable(propsObj.toList.map { case (name, propSchema) =>
                if (originalRequired.contains(name)) name -> propSchema else name -> makeNullable(propSchema)
              }))
            case None => foldedProps
          }
          acc.copy(
            fields = (k, nullableProps) :: acc.fields,
            addAdditionalProperties = true,
            requiredProperties = v.asObject.fold(List.empty[String])(_.keys.toList)
          )
        } else if (k == "type")
          acc.copy(
            fields = (k, v.foldWith(this)) :: acc.fields,
            addAdditionalProperties = acc.addAdditionalProperties || v.asString.contains("object")
          )
        else
          acc.copy(fields = (k, v.foldWith(this)) :: acc.fields)
      }

      // Detect if this object is part of a discriminated union by checking for a "discriminator" property.
      val isDiscriminatedUnion = value.contains("discriminator")

      val (addlPropsRemove, addlPropsAdd) =
        if (state.addAdditionalProperties && !isDiscriminatedUnion)
          (Set("additionalProperties"), List("additionalProperties" := false))
        else
          (Set.empty[String], Nil)

      val (requiredRemove, requiredAdd) =
        if (state.requiredProperties.nonEmpty)
          (Set("required"), List("required" := state.requiredProperties))
        else
          (Set.empty[String], Nil)

      val remove = addlPropsRemove ++ requiredRemove
      val fields = addlPropsAdd ++ requiredAdd ++ state.fields.filterNot { case (k, _) => remove.contains(k) }

      Json.fromFields(fields)
    }
  }

  /** OpenAI strict mode requires every property to be listed in `required`; optionality is expressed by making the property's type nullable
    * (https://platform.openai.com/docs/guides/structured-outputs#all-fields-must-be-required). Properties that were optional in the source
    * schema (absent from its original `required`) are made nullable here before the full `required` list is emitted.
    */
  private def makeNullable(propSchema: Json): Json =
    propSchema.asObject match {
      case Some(obj) =>
        obj("type") match {
          case Some(t) if t.isString =>
            if (t.asString.contains("null")) propSchema
            else Json.fromJsonObject(addNullToEnum(obj.add("type", Json.arr(t, Json.fromString("null")))))
          case Some(t) if t.isArray =>
            val types = t.asArray.getOrElse(Vector.empty)
            if (types.contains(Json.fromString("null"))) propSchema
            else Json.fromJsonObject(addNullToEnum(obj.add("type", Json.fromValues(types :+ Json.fromString("null")))))
          case _ =>
            if (isAlreadyNullableAnyOf(obj)) propSchema
            else Json.obj("anyOf" -> Json.arr(propSchema, Json.obj("type" -> Json.fromString("null"))))
        }
      case None => propSchema
    }

  /** F3 guard: a property may already be nullable via `anyOf: [X, {"type":"null"}]` (what tapir emits for `Option[<case class>]`, and a
    * common MCP idiom). Wrapping it again in `anyOf` would double-wrap it, so detect this shape and leave it unchanged.
    */
  private def isAlreadyNullableAnyOf(obj: JsonObject): Boolean =
    obj("anyOf")
      .flatMap(_.asArray)
      .exists(_.contains(Json.obj("type" -> Json.fromString("null"))))

  /** OpenAI requires optional enum properties to permit `null` in both `type` and `enum`
    * (https://platform.openai.com/docs/guides/structured-outputs#all-fields-must-be-required).
    */
  private def addNullToEnum(obj: JsonObject): JsonObject =
    obj("enum").flatMap(_.asArray) match {
      case Some(values) if !values.contains(Json.Null) => obj.add("enum", Json.fromValues(values :+ Json.Null))
      case _                                           => obj
    }
}
