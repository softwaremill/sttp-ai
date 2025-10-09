package sttp.openai.requests.completions.chat.message

import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.chat.message.ToolResource.{CodeInterpreterToolResource, FileSearchToolResource}
import ujson._

case class ToolResources(
    codeInterpreter: Option[CodeInterpreterToolResource] = None,
    fileSearch: Option[FileSearchToolResource] = None
)

object ToolResources {

  implicit val toolResourcesOptRW: SnakePickle.ReadWriter[Option[ToolResources]] = SnakePickle
    .readwriter[Value]
    .bimap[Option[ToolResources]](
      {
        case Some(resources) =>
          (resources.fileSearch, resources.codeInterpreter) match {
            case (Some(fileSearch), Some(codeInterpreter)) =>
              Obj("file_search" -> SnakePickle.writeJs(fileSearch), "code_interpreter" -> SnakePickle.writeJs(codeInterpreter))
            case (Some(fileSearch), None)      => Obj("file_search" -> SnakePickle.writeJs(fileSearch))
            case (None, Some(codeInterpreter)) => Obj("code_interpreter" -> SnakePickle.writeJs(codeInterpreter))
            case _                             => Obj()
          }
        case None => Obj()
      },
      json => {
        val map = json.obj
        if (map.nonEmpty) {
          val codeInterpreter: Option[CodeInterpreterToolResource] =
            map.get("code_interpreter").map(e => SnakePickle.read[CodeInterpreterToolResource](e))
          val fileSearch: Option[FileSearchToolResource] = map.get("file_search").map(e => SnakePickle.read[FileSearchToolResource](e))
          Some(ToolResources(codeInterpreter, fileSearch))
        } else {
          None
        }
      }
    )
}
