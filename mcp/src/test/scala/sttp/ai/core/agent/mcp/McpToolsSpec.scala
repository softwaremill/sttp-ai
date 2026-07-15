package sttp.ai.core.agent.mcp

import chimp.protocol.{CallToolResult, ToolContent}
import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class McpToolsSpec extends AnyFlatSpec with Matchers {

  behavior of "McpTools.renderResult"

  it should "join text blocks with newlines" in {
    val result = CallToolResult(List(ToolContent.Text(text = "line1"), ToolContent.Text(text = "line2")))
    McpTools.renderResult(result) shouldBe "line1\nline2"
  }

  it should "fall back to structuredContent when there are no content blocks" in {
    val result = CallToolResult(Nil, structuredContent = Some(Json.obj("answer" -> Json.fromInt(42))))
    McpTools.renderResult(result) shouldBe """{"answer":42}"""
  }

  it should "render an empty result as an empty string" in {
    McpTools.renderResult(CallToolResult(Nil)) shouldBe ""
  }

  it should "render non-text blocks as compact JSON" in {
    val result = CallToolResult(List(ToolContent.ResourceLink(uri = "file:///tmp/report.txt", name = Some("report"))))
    val rendered = McpTools.renderResult(result)
    rendered should include(""""uri":"file:///tmp/report.txt"""")
    rendered should include(""""resource_link"""")
  }

  it should "prefix the rendered content when isError is set" in {
    val result = CallToolResult(List(ToolContent.Text(text = "boom")), isError = true)
    McpTools.renderResult(result) shouldBe "Tool execution failed: boom"
  }
}
