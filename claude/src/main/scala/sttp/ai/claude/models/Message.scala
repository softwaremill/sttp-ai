package sttp.ai.claude.models

import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}

case class Message(
    role: String,
    content: List[ContentBlock]
)

object Message {
  def user(text: String): Message = Message(
    role = "user",
    content = List(ContentBlock.TextContent(text))
  )

  def user(content: List[ContentBlock]): Message = Message(
    role = "user",
    content = content
  )

  def assistant(text: String): Message = Message(
    role = "assistant",
    content = List(ContentBlock.TextContent(text))
  )

  def assistant(content: List[ContentBlock]): Message = Message(
    role = "assistant",
    content = content
  )

  def toolResult(toolUseId: String, result: String): Message = Message(
    role = "user",
    content = List(ContentBlock.ToolResultContent(toolUseId, result))
  )

  def toolResultWithError(toolUseId: String, error: String): Message = Message(
    role = "user",
    content = List(ContentBlock.ToolResultContent(toolUseId, error, Some(true)))
  )

  implicit val rw: ReadWriter[Message] = macroRW
}
