package sttp.openai.requests.completions.chat

import sttp.openai.json.SnakePickle
import ujson._

sealed trait ToolCall

object ToolCall {

  /** @param id
    *   The ID of the tool call.
    * @param function
    *   The function that the model called.
    */
  case class FunctionToolCall(id: Option[String], function: FunctionCall) extends ToolCall

  implicit val functionToolCallRW: SnakePickle.ReadWriter[FunctionToolCall] = SnakePickle
    .readwriter[Value]
    .bimap[FunctionToolCall](
      functionToolCall => {
        val baseObj = Obj("type" -> "function", "function" -> SnakePickle.writeJs(functionToolCall.function))
        functionToolCall.id.foreach(baseObj("id") = _)
        baseObj
      },
      json => FunctionToolCall(json.obj.get("id").map(_.str), SnakePickle.read[FunctionCall](json("function")))
    )

  implicit val toolCallRW: SnakePickle.ReadWriter[ToolCall] = SnakePickle
    .readwriter[Value]
    .bimap[ToolCall](
      { case functionToolCall: FunctionToolCall =>
        SnakePickle.writeJs(functionToolCall)
      },
      json => SnakePickle.read[FunctionToolCall](json)
    )
}
