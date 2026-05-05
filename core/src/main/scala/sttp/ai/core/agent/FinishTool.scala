package sttp.ai.core.agent

import sttp.ai.core.json.SnakePickle
import sttp.apispec.Schema
import sttp.tapir.{Schema => TapirSchema}

case class FinishInput(answer: String)

object FinishInput {
  implicit val rw: SnakePickle.ReadWriter[FinishInput] = SnakePickle.macroRW
  implicit val schema: TapirSchema[FinishInput] = TapirSchema.derived
}

object FinishTool {
  val ToolName: String = "finish"

  def apply(): AgentTool[FinishInput] =
    AgentTool.fromFunction(
      ToolName,
      "Call this tool when you have a final answer to provide to the user. This will end the agent loop."
    )(_.answer)

  def withResponseSchema[T](rs: ResponseSchema[T]): AgentTool[T] = new AgentTool[T] {
    override val name: String = ToolName
    override val description: String =
      "Call this tool with your final answer as a structured payload conforming to the response schema. " +
        "This ends the agent loop." +
        rs.description.fold("")(d => s" $d")
    override val jsonSchema: Schema = rs.schema
    override val readWriter: SnakePickle.ReadWriter[T] = rs.readWriter
    override def execute(input: T): String = SnakePickle.write(input)(rs.readWriter)
  }
}
