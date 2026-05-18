package sttp.ai.openai.requests.completions.chat.message

import sttp.ai.core.json.SnakePickle
import sttp.ai.openai.requests.completions.chat.ToolCall
import ujson._

@upickle.implicits.key("role")
sealed trait Message

object Message {
  @upickle.implicits.key("system")
  case class SystemMessage(content: String, name: Option[String] = None) extends Message
  @upickle.implicits.key("user")
  case class UserMessage(content: Content, name: Option[String] = None) extends Message
  @upickle.implicits.key("assistant")
  case class AssistantMessage(content: String, name: Option[String] = None, toolCalls: Seq[ToolCall] = Nil) extends Message
  @upickle.implicits.key("tool")
  case class ToolMessage(content: String, toolCallId: String) extends Message

  object ToolMessage {
    def apply(content: String, toolCallId: String): ToolMessage = new ToolMessage(content, toolCallId)

    def apply[T: SnakePickle.Writer](content: T, toolCallId: String): ToolMessage =
      new ToolMessage(SnakePickle.write(content), toolCallId)

    implicit val toolMessageRW: SnakePickle.ReadWriter[ToolMessage] = SnakePickle.macroRW[ToolMessage]
  }

  implicit val systemMessageRW: SnakePickle.ReadWriter[SystemMessage] = SnakePickle.macroRW[SystemMessage]
  implicit val userMessageRW: SnakePickle.ReadWriter[UserMessage] = SnakePickle.macroRW[UserMessage]
  implicit val assistantMessageRW: SnakePickle.ReadWriter[AssistantMessage] = SnakePickle.macroRW[AssistantMessage]

  implicit val messageRW: SnakePickle.ReadWriter[Message] = SnakePickle.macroRW[Message]
}
