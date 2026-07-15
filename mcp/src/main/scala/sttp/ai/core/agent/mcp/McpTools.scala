package sttp.ai.core.agent.mcp

import chimp.protocol.{CallToolResult, ToolContent}
import io.circe.syntax.*

object McpTools {

  private[mcp] def renderResult(result: CallToolResult): String = {
    val blocks = result.content.map {
      case ToolContent.Text(_, text) => text
      case other                     => other.asJson.noSpaces
    }
    val body =
      if (blocks.nonEmpty) blocks.mkString("\n")
      else result.structuredContent.map(_.noSpaces).getOrElse("")
    if (result.isError) s"Tool execution failed: $body" else body
  }
}
