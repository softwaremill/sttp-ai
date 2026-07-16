package sttp.ai.core.agent.mcp

import chimp.protocol.{CallToolResult, ListToolsResponse, ToolContent, ToolDefinition}
import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity

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

  it should "render an error with no content with a placeholder" in {
    McpTools.renderResult(CallToolResult(Nil, isError = true)) shouldBe "Tool execution failed: (no details provided by the MCP server)"
  }

  private given MonadError[Identity] = IdentityMonad

  private val addSchema = Json.obj(
    "type" -> Json.fromString("object"),
    "properties" -> Json.obj(
      "a" -> Json.obj("type" -> Json.fromString("number")),
      "b" -> Json.obj("type" -> Json.fromString("number"))
    ),
    "required" -> Json.arr(Json.fromString("a"), Json.fromString("b"))
  )

  private def toolDef(name: String, description: Option[String] = None, title: Option[String] = None, schema: Json = addSchema) =
    ToolDefinition(name = name, description = description, inputSchema = schema, title = title)

  behavior of "McpTools.fromClient"

  it should "discover tools across all pages" in {
    val client = new StubMcpClient(
      pages = Seq(
        ListToolsResponse(tools = List(toolDef("add")), nextCursor = Some("1")),
        ListToolsResponse(tools = List(toolDef("sub")))
      )
    )
    val tools = McpTools.fromClient(client)
    tools.map(_.name) shouldBe Seq("add", "sub")
    client.recordedCursors shouldBe Vector(None, Some("1"))
  }

  it should "use description, falling back to title, then name" in {
    val client = new StubMcpClient(
      pages = Seq(
        ListToolsResponse(tools =
          List(
            toolDef("add", description = Some("Adds numbers"), title = Some("Adder")),
            toolDef("sub", title = Some("Subtractor")),
            toolDef("mul")
          )
        )
      )
    )
    McpTools.fromClient(client).map(_.description) shouldBe Seq("Adds numbers", "Subtractor", "mul")
  }

  it should "convert the input schema to an apispec schema" in {
    val client = new StubMcpClient(pages = Seq(ListToolsResponse(tools = List(toolDef("add")))))
    val tool = McpTools.fromClient(client).head
    tool.jsonSchema.properties.keySet shouldBe Set("a", "b")
  }

  it should "fail with a descriptive error when a tool's input schema cannot be decoded" in {
    val client = new StubMcpClient(pages = Seq(ListToolsResponse(tools = List(toolDef("bad", schema = Json.fromString("nope"))))))
    val ex = the[McpToolConversionException] thrownBy McpTools.fromClient(client)
    ex.getMessage should include("bad")
  }

  it should "expose the prefixed name to the LLM but call the server with the original name" in {
    val client = new StubMcpClient(
      pages = Seq(ListToolsResponse(tools = List(toolDef("add")))),
      callResults = Map("add" -> CallToolResult(List(ToolContent.Text(text = "3"))))
    )
    val tool = McpTools.fromClient(client, namePrefix = Some("calc")).head
    tool.name shouldBe "calc_add"
    tool.execute(Map("a" -> Json.fromInt(1), "b" -> Json.fromInt(2))) shouldBe "3"
    client.recordedCalls shouldBe Vector("add" -> Json.obj("a" -> Json.fromInt(1), "b" -> Json.fromInt(2)))
  }

  it should "keep original names when no prefix is given" in {
    val client = new StubMcpClient(pages = Seq(ListToolsResponse(tools = List(toolDef("add")))))
    McpTools.fromClient(client).head.name shouldBe "add"
  }
}
