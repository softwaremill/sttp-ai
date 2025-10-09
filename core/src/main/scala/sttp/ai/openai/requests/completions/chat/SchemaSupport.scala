package sttp.openai.requests.completions.chat

import io.circe.syntax._
import io.circe.{DecodingFailure, Json, JsonNumber, JsonObject}
import sttp.apispec.Schema
import sttp.apispec.circe._
import sttp.openai.json.SnakePickle
import ujson._
import ujson.circe.CirceJson

object SchemaSupport {

  private case class ParseException(circeException: DecodingFailure) extends Exception("Failed to parse JSON schema", circeException)

  val schemaRW: SnakePickle.ReadWriter[Schema] = SnakePickle
    .readwriter[Value]
    .bimap(
      s => CirceJson.transform(s.asJson.deepDropNullValues.foldWith(schemaFolder), upickle.default.reader[Value]),
      v =>
        upickle.default.transform(v).to(CirceJson).as[Schema] match {
          case Left(e)  => throw ParseException(e)
          case Right(s) => s
        }
    )

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
      val state = value.toList.foldRight(FolderState(Nil, addAdditionalProperties = false, Nil)) { case ((k, v), acc) =>
        if (k == "properties")
          acc.copy(
            fields = (k, v.foldWith(this)) :: acc.fields,
            addAdditionalProperties = true,
            requiredProperties = v.asObject.fold(List.empty[String])(_.keys.toList)
          )
        else if (k == "type")
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
          (Set(), Nil)

      val (requiredRemove, requiredAdd) =
        if (state.requiredProperties.nonEmpty)
          (Set("required"), List("required" := state.requiredProperties))
        else
          (Set(), Nil)

      val remove = addlPropsRemove ++ requiredRemove
      val fields = addlPropsAdd ++ requiredAdd ++ state.fields.filterNot { case (k, _) => remove.contains(k) }

      Json.fromFields(fields)
    }
  }

}
