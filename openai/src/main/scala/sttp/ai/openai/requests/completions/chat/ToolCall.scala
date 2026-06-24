package sttp.ai.openai.requests.completions.chat

sealed trait ToolCall

object ToolCall {

  /** @param id
    *   The ID of the tool call.
    * @param function
    *   The function that the model called.
    */
  case class FunctionToolCall(id: Option[String], function: FunctionCall) extends ToolCall
}
