package sttp.openai.requests.completions.chat.message

import sttp.openai.json.{SerializationHelpers, SnakePickle}
import sttp.openai.requests.completions.chat.SchemaSupport
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TSchema}
import ujson._

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
  @upickle.implicits.key("function")
  case class FunctionTool(
      name: String,
      description: Option[String] = None,
      parameters: Option[Map[String, Value]] = None,
      strict: Option[Boolean] = Some(false)
  ) extends Tool

  object FunctionTool {

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
    def withSchema[T: TSchema](name: String, description: Option[String] = None): FunctionTool = {
      val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
      val schemaJson = SnakePickle.writeJs(schema)(SchemaSupport.schemaRW)
      FunctionTool(name, description, Some(schemaJson.obj.toMap), Some(false))
    }

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
    def withSchema[T: TSchema](name: String, description: Option[String], strict: Option[Boolean]): FunctionTool = {
      val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
      val schemaJson = SnakePickle.writeJs(schema)(SchemaSupport.schemaRW)
      FunctionTool(name, description, Some(schemaJson.obj.toMap), strict)
    }
  }

  implicit val functionToolW: SnakePickle.Writer[FunctionTool] =
    SerializationHelpers.withNestedDiscriminatorWriter("function", "function")(SnakePickle.macroW)
  implicit val functionToolR: SnakePickle.Reader[FunctionTool] =
    SerializationHelpers.withNestedDiscriminatorReader("function", "function")(SnakePickle.macroR)

  object CustomTool {

    sealed trait Format

    object Format {

      /** Unconstrained free-form text.
        */
      @upickle.implicits.key("text")
      case class Text() extends Format

      /** A grammar defined by the user.
        *
        * @param definition
        *   The grammar definition.
        * @param syntax
        *   The syntax of the grammar definition. One of lark or regex.
        */
      @upickle.implicits.key("grammar")
      case class Grammar(
          definition: String,
          syntax: String
      ) extends Format

      implicit val textRW: SnakePickle.ReadWriter[Text] = SnakePickle.macroRW
      implicit val grammarRW: SnakePickle.ReadWriter[Grammar] = SnakePickle.macroRW
      implicit val formatRW: SnakePickle.ReadWriter[Format] = SnakePickle.macroRW
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
  @upickle.implicits.key("custom")
  case class CustomTool(
      name: String,
      description: Option[String] = None,
      format: Option[CustomTool.Format] = None
  ) extends Tool

  implicit val customToolW: SnakePickle.Writer[CustomTool] =
    SerializationHelpers.withNestedDiscriminatorWriter("custom", "custom")(SnakePickle.macroW)
  implicit val customToolR: SnakePickle.Reader[CustomTool] =
    SerializationHelpers.withNestedDiscriminatorReader("custom", "custom")(SnakePickle.macroR)

  implicit val toolRW: SnakePickle.ReadWriter[Tool] = SnakePickle
    .readwriter[Value]
    .bimap[Tool](
      {
        case functionTool: FunctionTool =>
          SnakePickle.writeJs(functionTool)
        case customTool: CustomTool =>
          SnakePickle.writeJs(customTool)
      },
      json =>
        json("type").str match {
          case "function" => SnakePickle.read[FunctionTool](json)
          case "custom"   => SnakePickle.read[CustomTool](json)
        }
    )
}
