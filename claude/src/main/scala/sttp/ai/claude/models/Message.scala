package sttp.ai.claude.models

case class Message(
    role: String,
    content: List[ContentBlock]
)

object Message {
  def user(text: String): Message = Message(
    role = "user",
    content = List(ContentBlock.Text(text))
  )

  def user(content: List[ContentBlock]): Message = Message(
    role = "user",
    content = content
  )

  def assistant(text: String): Message = Message(
    role = "assistant",
    content = List(ContentBlock.Text(text))
  )

  def assistant(content: List[ContentBlock]): Message = Message(
    role = "assistant",
    content = content
  )

  def toolResult(toolUseId: String, result: String): Message = Message(
    role = "user",
    content = List(ContentBlock.ToolResult(toolUseId, result))
  )

  def toolResultWithError(toolUseId: String, error: String): Message = Message(
    role = "user",
    content = List(ContentBlock.ToolResult(toolUseId, error, Some(true)))
  )
}
