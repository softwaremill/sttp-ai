package sttp.openai.requests.completions.chat.message

import sttp.openai.json.SnakePickle
import sttp.openai.requests.assistants.Tool
import ujson.{Obj, Value}

case class Attachment(fileId: Option[String] = None, tools: Option[Seq[Tool]] = None)

object Attachment {
  implicit val toolResourcesOptRW: SnakePickle.ReadWriter[Attachment] = SnakePickle
    .readwriter[Value]
    .bimap[Attachment](
      attachment =>
        (attachment.fileId, attachment.tools) match {
          case (Some(fileId), Some(tools)) => Obj("file_id" -> fileId, "tools" -> SnakePickle.writeJs(tools))
          case (Some(fileId), None)        => Obj("file_id" -> fileId)
          case (None, Some(tools))         => Obj("tools" -> SnakePickle.writeJs(tools))
          case _                           => Obj()
        },
      json => {
        val map = json.obj
        val fileId: Option[String] = map.get("file_id").map(_.str)
        val tools: Option[Seq[Tool]] = map.get("tools").map(_.arr.map(e => SnakePickle.read[Tool](e)).toList).filter(_.nonEmpty)
        Attachment(fileId, tools)
      }
    )
}
