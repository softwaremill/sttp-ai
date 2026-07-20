package sttp.ai.core.agent.mcp

import chimp.protocol.*
import io.circe.Json
import sttp.shared.Identity

/** In-memory MCP client stub: serves canned `tools/list` pages and `tools/call` results, and records invocations. A `nextCursor` in a page
  * must be the (string) index of the next page in `pages`.
  */
class StubMcpClient(
    pages: Seq[ListToolsResponse],
    callResults: Map[String, CallToolResult] = Map.empty
) extends UnsupportedMcpClient[Identity] {
  var recordedCalls: Vector[(String, Json)] = Vector.empty
  var recordedCursors: Vector[Option[Cursor]] = Vector.empty

  override def listTools(cursor: Option[Cursor]): ListToolsResponse = {
    recordedCursors = recordedCursors :+ cursor
    pages(cursor.fold(0)(_.toInt))
  }

  override def callTool(name: String, arguments: Json): CallToolResult = {
    recordedCalls = recordedCalls :+ (name -> arguments)
    callResults.getOrElse(name, CallToolResult(List(ToolContent.Text(text = s"no canned result for $name"))))
  }
}
