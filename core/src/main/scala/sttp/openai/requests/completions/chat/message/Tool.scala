package sttp.openai.requests.completions.chat.message

import sttp.openai.json.SnakePickle
import ujson._

sealed trait Tool

object Tool {

  /** @param description
    *   A description of what the function does, used by the model to choose when and how to call the function.
    * @param name
    *   The name of the function to be called. Must be a-z, A-Z, 0-9, or contain underscores and dashes, with a maximum length of 64.
    * @param parameters
    *   The parameters the functions accepts, described as a JSON Schema object
    * @param strict
    *   Whether to enable strict schema adherence when generating the function call. If set to true, the model will follow the exact schema
    *   defined in the parameters field. Only a subset of JSON Schema is supported when strict is true.
    */
  case class FunctionTool(description: String, name: String, parameters: Map[String, Value], strict: Boolean = false) extends Tool

  implicit val functionToolRW: SnakePickle.ReadWriter[FunctionTool] = SnakePickle
    .readwriter[Value]
    .bimap[FunctionTool](
      functionTool => {
        val baseObj = Obj(
          "description" -> functionTool.description,
          "name" -> functionTool.name,
          "parameters" -> functionTool.parameters
        )
        if (functionTool.strict) baseObj("strict") = true
        baseObj
      },
      json =>
        FunctionTool(
          json("description").str,
          json("name").str,
          json("parameters").obj.toMap,
          json.obj.get("strict").exists(_.bool)
        )
    )

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
          Obj("type" -> "function", "function" -> SnakePickle.writeJs(functionTool))
        case CodeInterpreterTool =>
          Obj("type" -> "code_interpreter")
        case FileSearchTool =>
          Obj("type" -> "file_search")
      },
      json =>
        json("type").str match {
          case "function"         => SnakePickle.read[FunctionTool](json("function"))
          case "code_interpreter" => CodeInterpreterTool
          case "file_search"      => FileSearchTool
        }
    )
}
