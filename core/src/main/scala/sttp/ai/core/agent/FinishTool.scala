package sttp.ai.core.agent

import sttp.ai.core.json.SnakePickle
import sttp.tapir.Schema

case class FinishInput(answer: String)

object FinishInput {
  implicit val rw: SnakePickle.ReadWriter[FinishInput] = SnakePickle.macroRW
  implicit val schema: Schema[FinishInput] = Schema.derived
}

object FinishTool {
  val ToolName: String = "finish"

  def apply(): AgentTool[FinishInput] =
    AgentTool.fromFunction(
      ToolName,
      "Call this tool when you have a final answer to provide to the user. This will end the agent loop."
    )(_.answer)
}
