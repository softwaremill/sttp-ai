package sttp.ai.core.agent

import sttp.apispec.Schema
import sttp.ai.core.json.SnakePickle
import sttp.tapir.{Schema => TapirSchema}

case class FinishInput(answer: String)

object FinishInput {
  implicit val rw: SnakePickle.ReadWriter[FinishInput] = SnakePickle.macroRW
  implicit val schema: TapirSchema[FinishInput] = TapirSchema.derived
}

object FinishTool {
  val ToolName: String = "finish"

  /** Default finish tool, taking a free-form `answer: String`. */
  def apply(): AgentTool[FinishInput] =
    AgentTool.fromFunction(
      ToolName,
      "Call this tool when you have a final answer to provide to the user. This will end the agent loop."
    )(_.answer)

  /** Finish tool whose input schema is the user's `T`. The model is forced to call `finish(...)` with a JSON object matching `T`'s schema;
    * the agent loop then round-trips the parsed input back to a JSON string, which is what `Agent.run` returns as `finalAnswer`.
    */
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
