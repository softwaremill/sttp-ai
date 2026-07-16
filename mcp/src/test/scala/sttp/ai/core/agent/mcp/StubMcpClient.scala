package sttp.ai.core.agent.mcp

import chimp.client.McpClient
import chimp.protocol.*
import io.circe.Json
import sttp.shared.Identity

/** In-memory MCP client stub: serves canned `tools/list` pages and `tools/call` results, and records invocations. A `nextCursor` in a page
  * must be the (string) index of the next page in `pages`.
  */
class StubMcpClient(
    pages: Seq[ListToolsResponse],
    callResults: Map[String, CallToolResult] = Map.empty
) extends McpClient[Identity] {
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

  override def ping(): Unit = ()
  override def close(): Unit = ()
  override def serverCapabilities: ServerCapabilities = ServerCapabilities()
  override def serverInfo: Implementation = Implementation("fake-server", "0.0.1")
  override def listPrompts(cursor: Option[Cursor]): ListPromptsResult = unsupported
  override def getPrompt(name: String, arguments: Map[String, String]): GetPromptResult = unsupported
  override def listResources(cursor: Option[Cursor]): ListResourcesResult = unsupported
  override def listResourceTemplates(cursor: Option[Cursor]): ListResourceTemplatesResult = unsupported
  override def readResource(uri: String): ReadResourceResult = unsupported
  override def complete(ref: CompleteRef, argument: CompleteArgument): CompleteResult = unsupported
  override def setLoggingLevel(level: LoggingLevel): Unit = unsupported
  override def sendProgress(token: ProgressToken, progress: Double, total: Option[Double], message: Option[String]): Unit = unsupported

  private def unsupported: Nothing = throw new UnsupportedOperationException("not used by McpTools")
}
