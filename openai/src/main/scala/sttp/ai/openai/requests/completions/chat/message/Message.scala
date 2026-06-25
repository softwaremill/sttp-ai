package sttp.ai.openai.requests.completions.chat.message

import io.circe.Encoder
import io.circe.syntax._
import sttp.ai.openai.requests.completions.chat.ToolCall

sealed trait Message

object Message {
  case class System(content: String, name: Option[String] = None) extends Message
  case class User(content: Content, name: Option[String] = None) extends Message
  case class Assistant(content: String, name: Option[String] = None, toolCalls: Seq[ToolCall] = Nil) extends Message
  case class Tool(content: String, toolCallId: String) extends Message

  object Tool {
    def apply(content: String, toolCallId: String): Tool = new Tool(content, toolCallId)

    def apply[T: Encoder](content: T, toolCallId: String): Tool =
      new Tool(content.asJson.noSpaces, toolCallId)
  }
}
