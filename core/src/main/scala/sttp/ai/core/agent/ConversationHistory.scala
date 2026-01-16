package sttp.ai.core.agent

sealed trait ConversationEntry

object ConversationEntry {
  case class UserPrompt(content: String) extends ConversationEntry
  case class AssistantResponse(content: String, toolCalls: Seq[ToolCall]) extends ConversationEntry
  case class ToolResult(toolCallId: String, toolName: String, result: String) extends ConversationEntry
  case class IterationMarker(currentIteration: Int, maxIterations: Int) extends ConversationEntry
}

case class ToolCall(
    id: String,
    toolName: String,
    input: String
)

case class ConversationHistory(entries: Seq[ConversationEntry]) {
  def addUserPrompt(content: String): ConversationHistory =
    ConversationHistory(entries :+ ConversationEntry.UserPrompt(content))

  def addAssistantResponse(content: String, toolCalls: Seq[ToolCall]): ConversationHistory =
    ConversationHistory(entries :+ ConversationEntry.AssistantResponse(content, toolCalls))

  def addToolResult(toolCallId: String, toolName: String, result: String): ConversationHistory =
    ConversationHistory(entries :+ ConversationEntry.ToolResult(toolCallId, toolName, result))

  def addIterationMarker(currentIteration: Int, maxIterations: Int): ConversationHistory =
    ConversationHistory(entries :+ ConversationEntry.IterationMarker(currentIteration, maxIterations))
}

object ConversationHistory {
  def empty: ConversationHistory = ConversationHistory(Seq.empty)

  def withInitialPrompt(prompt: String): ConversationHistory =
    ConversationHistory(Seq(ConversationEntry.UserPrompt(prompt)))
}
