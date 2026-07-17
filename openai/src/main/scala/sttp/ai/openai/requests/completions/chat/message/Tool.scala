package sttp.ai.openai.requests.completions.chat.message

import io.circe.Json
import sttp.ai.openai.requests.completions.chat.SchemaSupport
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TSchema}

sealed trait Tool

object Tool {

  /** Function tool definition – supports structured outputs by allowing the optional `strict` flag.
    *
    * @param name
    *   The name of the function to be called. Must be a-z, A-Z, 0-9, or contain underscores and dashes, with a maximum length of 64.
    * @param description
    *   A description of what the function does, used by the model to choose when and how to call the function.
    * @param parameters
    *   The parameters the functions accepts, described as a JSON Schema object. Omitting parameters defines a function with an empty
    *   parameter list.
    * @param strict
    *   Whether to enable strict schema adherence when generating the function call. Defaults to false.
    */
  case class Function(
      name: String,
      description: Option[String] = None,
      parameters: Option[Map[String, Json]] = None,
      strict: Option[Boolean] = Some(false)
  ) extends Tool

  object Function {

    /** Create a FunctionTool with schema automatically generated from type T.
      *
      * @param name
      *   The name of the function to be called.
      * @param description
      *   A description of what the function does.
      * @tparam T
      *   The type to generate schema from.
      * @return
      *   A FunctionTool with auto-generated schema.
      */
    def withSchema[T: TSchema](name: String, description: Option[String] = None): Function =
      withSchema[T](name, description, Some(false))

    /** Create a FunctionTool with schema automatically generated from type T and strict flag.
      *
      * @param name
      *   The name of the function to be called.
      * @param description
      *   A description of what the function does.
      * @param strict
      *   When set to true, Structured Outputs validation will be enforced.
      * @tparam T
      *   The type to generate schema from.
      * @return
      *   A FunctionTool with auto-generated schema and strict flag.
      */
    def withSchema[T: TSchema](name: String, description: Option[String], strict: Option[Boolean]): Function = {
      val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
      val schemaJson = if (strict.contains(true)) SchemaSupport.normalizeForStrict(schema) else SchemaSupport.schemaCodec(schema)
      Function(name, description, schemaJson.asObject.map(_.toMap), strict)
    }
  }

  object Custom {

    sealed trait Format
    object Format {

      /** Unconstrained free-form text.
        */
      case class Text() extends Format

      /** A grammar defined by the user.
        *
        * @param definition
        *   The grammar definition.
        * @param syntax
        *   The syntax of the grammar definition. One of lark or regex.
        */
      case class Grammar(definition: String, syntax: String) extends Format
    }
  }

  /** A custom tool that processes input using a specified format.
    *
    * @param name
    *   The name of the custom tool, used to identify it in tool calls.
    * @param description
    *   Optional description of the custom tool, used to provide more context.
    * @param format
    *   The input format for the custom tool. Default is unconstrained text.
    */
  case class Custom(
      name: String,
      description: Option[String] = None,
      format: Option[Custom.Format] = None
  ) extends Tool
}
