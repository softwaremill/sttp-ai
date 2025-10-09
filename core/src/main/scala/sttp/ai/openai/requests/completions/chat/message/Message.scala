package sttp.openai.requests.completions.chat.message

import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.chat.ToolCall
import ujson._

sealed trait Message

object Message {
  case class SystemMessage(content: String, name: Option[String] = None) extends Message
  case class UserMessage(content: Content, name: Option[String] = None) extends Message
  case class AssistantMessage(content: String, name: Option[String] = None, toolCalls: Seq[ToolCall] = Nil) extends Message
  case class ToolMessage(content: String, toolCallId: String) extends Message

  object ToolMessage {
    def apply(content: String, toolCallId: String): ToolMessage = new ToolMessage(content, toolCallId)

    def apply[T: SnakePickle.Writer](content: T, toolCallId: String): ToolMessage =
      new ToolMessage(SnakePickle.write(content), toolCallId)

    implicit val toolMessageRW: SnakePickle.ReadWriter[ToolMessage] =
      SnakePickle
        .readwriter[Value]
        .bimap[ToolMessage](
          msg => Obj("role" -> "tool", "content" -> msg.content, "tool_call_id" -> msg.toolCallId),
          json => ToolMessage(json("content").str, json("tool_call_id").str)
        )
  }

  implicit val systemMessageRW: SnakePickle.ReadWriter[SystemMessage] =
    SnakePickle
      .readwriter[Value]
      .bimap[SystemMessage](
        msg => {
          val baseObj = Obj("role" -> "system", "content" -> msg.content)
          msg.name.foreach(name => baseObj("name") = name)
          baseObj
        },
        json => SystemMessage(json("content").str, json.obj.get("name").map(_.str))
      )

  implicit val userMessageRW: SnakePickle.ReadWriter[UserMessage] =
    SnakePickle
      .readwriter[Value]
      .bimap[UserMessage](
        msg => {
          val baseObj = Obj("role" -> "user", "content" -> SnakePickle.writeJs(msg.content))
          msg.name.foreach(name => baseObj("name") = name)
          baseObj
        },
        json => UserMessage(SnakePickle.read[Content](json("content")), json.obj.get("name").map(_.str))
      )

  implicit val assistantMessageRW: SnakePickle.ReadWriter[AssistantMessage] =
    SnakePickle
      .readwriter[Value]
      .bimap[AssistantMessage](
        msg => {
          val baseObj = Obj("role" -> "assistant", "content" -> msg.content)
          msg.name.foreach(name => baseObj("name") = name)
          if (msg.toolCalls.nonEmpty) {
            baseObj("tool_calls") = SnakePickle.writeJs(msg.toolCalls)
          }
          baseObj
        },
        json =>
          AssistantMessage(
            json("content").str,
            json.obj.get("name").map(_.str),
            SnakePickle.read[Seq[ToolCall]](json.obj("tool_calls"))
          )
      )

  implicit val messageRW: SnakePickle.ReadWriter[Message] =
    SnakePickle
      .readwriter[Value]
      .bimap[Message](
        {
          case msg: SystemMessage    => SnakePickle.writeJs(msg)
          case msg: UserMessage      => SnakePickle.writeJs(msg)
          case msg: AssistantMessage => SnakePickle.writeJs(msg)
          case msg: ToolMessage      => SnakePickle.writeJs(msg)
        },
        json =>
          json("role").str match {
            case "system"    => SnakePickle.read[SystemMessage](json)
            case "user"      => SnakePickle.read[UserMessage](json)
            case "assistant" => SnakePickle.read[AssistantMessage](json)
            case "tool"      => SnakePickle.read[ToolMessage](json)
          }
      )
}
