package sttp.ai.openai.requests.assistants

import io.circe.Json
import sttp.ai.openai.requests.completions.chat.SchemaSupport
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TSchema}

sealed trait Tool

object Tool {

  /** Function tool definition – supports structured outputs by allowing the optional `strict` flag.
    *
    * @param description
    *   A description of what the function does, used by the model to choose when and how to call the function.
    * @param name
    *   The name of the function to be called. Must be a-z, A-Z, 0-9, or contain underscores and dashes, with a maximum length of 64.
    * @param parameters
    *   The parameters the functions accepts, described as a JSON Schema object.
    * @param strict
    *   When set to `true`, [Structured Outputs](https://platform.openai.com/docs/guides/structured-outputs) validation will be enforced by
    *   the OpenAI API. Defaults to `None`, meaning the field is omitted in the outgoing JSON.
    */
  case class Function(
      description: String,
      name: String,
      parameters: Map[String, Json],
      strict: Option[Boolean] = None
  ) extends Tool

  object Function {

    /** Create a FunctionTool with schema automatically generated from type T.
      *
      * @param description
      *   A description of what the function does.
      * @param name
      *   The name of the function to be called.
      * @tparam T
      *   The type to generate schema from.
      * @return
      *   A FunctionTool with auto-generated schema.
      */
    def withTapirSchema[T: TSchema](description: String, name: String): Function =
      withTapirSchema(description, name, None)

    /** Create a FunctionTool with schema automatically generated from type T and strict flag.
      *
      * @param description
      *   A description of what the function does.
      * @param name
      *   The name of the function to be called.
      * @param strict
      *   When set to true, Structured Outputs validation will be enforced.
      * @tparam T
      *   The type to generate schema from.
      * @return
      *   A FunctionTool with auto-generated schema and strict flag.
      */
    def withTapirSchema[T: TSchema](description: String, name: String, strict: Option[Boolean]): Function = {
      val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
      val schemaJson = if (strict.contains(true)) SchemaSupport.normalizeForStrict(schema) else SchemaSupport.schemaCodec(schema)
      Function(description, name, schemaJson.asObject.map(_.toMap).getOrElse(Map.empty), strict)
    }
  }

  /** Code interpreter tool. The type of tool being defined: code_interpreter */
  case object CodeInterpreter extends Tool

  /** file_search tool. The type of tool being defined: file_search */
  case object FileSearch extends Tool
}
