package sttp.openai.requests.assistants

import sttp.openai.json.{SerializationHelpers, SnakePickle}
import sttp.openai.requests.completions.chat.SchemaSupport
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TSchema}
import ujson._

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
  @upickle.implicits.key("function")
  case class FunctionTool(
      description: String,
      name: String,
      parameters: Map[String, Value],
      strict: Option[Boolean] = None
  ) extends Tool

  object FunctionTool {

    /** Create a FunctionTool with schema automatically generated from type T. This provides the same convenience as SchematizedFunctionTool
      * but keeps the Tool trait clean.
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
    def withTapirSchema[T: TSchema](description: String, name: String): FunctionTool = {
      val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
      val schemaJson = SnakePickle.writeJs(schema)(SchemaSupport.schemaRW)
      FunctionTool(description, name, schemaJson.obj.toMap, None)
    }

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
    def withTapirSchema[T: TSchema](description: String, name: String, strict: Option[Boolean]): FunctionTool = {
      val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
      val schemaJson = SnakePickle.writeJs(schema)(SchemaSupport.schemaRW)
      FunctionTool(description, name, schemaJson.obj.toMap, strict)
    }
  }

  implicit val functionToolW: SnakePickle.Writer[FunctionTool] =
    SerializationHelpers.withNestedDiscriminatorWriter("function", "function")(SnakePickle.macroW)
  implicit val functionToolR: SnakePickle.Reader[FunctionTool] =
    SerializationHelpers.withNestedDiscriminatorReader("function", "function")(SnakePickle.macroR)

  /** Code interpreter tool
    *
    * The type of tool being defined: code_interpreter
    */
  case object CodeInterpreterTool extends Tool

  /** file_search tool
    *
    * The type of tool being defined: file_search
    */
  case object FileSearchTool extends Tool

  implicit val toolRW: SnakePickle.ReadWriter[Tool] = SnakePickle
    .readwriter[Value]
    .bimap[Tool](
      {
        case functionTool: FunctionTool =>
          SnakePickle.writeJs(functionTool)
        case CodeInterpreterTool =>
          Obj("type" -> "code_interpreter")
        case FileSearchTool =>
          Obj("type" -> "file_search")
      },
      json =>
        json("type").str match {
          case "function"         => SnakePickle.read[FunctionTool](json)
          case "code_interpreter" => CodeInterpreterTool
          case "file_search"      => FileSearchTool
        }
    )
}
