package sttp.ai.core.agent

import ujson.Value

class FinishTool extends AgentTool {
  override def name: String = FinishTool.ToolName

  override def description: String =
    "Call this tool when you have a final answer to provide to the user. This will end the agent loop."

  override def parameters: Map[String, ParameterSpec] = Map(
    "answer" -> ParameterSpec(
      dataType = ParameterType.String,
      description = "The final answer to provide to the user",
    )
  )

  override def execute(input: Map[String, Value]): String =
    input.get("answer").map(_.str).getOrElse("")
}

object FinishTool {
  def apply(): FinishTool = new FinishTool()

  val ToolName: String = "finish"
}
