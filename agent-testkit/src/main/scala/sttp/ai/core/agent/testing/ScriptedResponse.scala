package sttp.ai.core.agent.testing

import sttp.ai.core.agent.{AgentResponse, StopReason, ToolCall}

/** Readable constructors for the [[AgentResponse]] values that make up a [[ScriptedAgent]] script. All return plain [[AgentResponse]], so
  * hand-built responses can be mixed in for cases these don't cover (custom stop reasons, explicit tool-call ids).
  */
object ScriptedResponse {

  /** A final text answer: terminates the agent loop (StopReason.EndTurn, no tool calls). */
  def text(content: String): AgentResponse = AgentResponse(content, Seq.empty, StopReason.EndTurn)

  /** A response requesting a single tool call, with arguments given as a JSON string. The call id is generated as `call_1`. */
  def toolCall(name: String, argsJson: String): AgentResponse = toolCalls(name -> argsJson)

  /** A response requesting one or more tool calls as (toolName, argsJson) pairs. Call ids are generated as `call_1`, `call_2`, ... */
  def toolCalls(calls: (String, String)*): AgentResponse = AgentResponse("", makeCalls(calls), StopReason.ToolUse)

  /** A response with both text content and tool calls. */
  def textWithToolCalls(content: String, calls: (String, String)*): AgentResponse =
    AgentResponse(content, makeCalls(calls), StopReason.ToolUse)

  /** A response cut short by the token limit: the loop stops with FinishReason.TokenLimit. */
  def maxTokens(content: String): AgentResponse = AgentResponse(content, Seq.empty, StopReason.MaxTokens)

  private def makeCalls(calls: Seq[(String, String)]): Seq[ToolCall] =
    calls.zipWithIndex.map { case ((name, argsJson), idx) =>
      ToolCall(id = s"call_${idx + 1}", toolName = name, input = argsJson)
    }
}
