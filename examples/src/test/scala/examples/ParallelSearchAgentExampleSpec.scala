package examples

import chimp.client.McpClient
import chimp.client.transport.ClientHttpTransport
import chimp.protocol.Implementation
import chimp.server.{tool, McpServer, ToolResult}
import io.circe.{Codec, Json}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.supervised
import sttp.ai.core.agent.FinishReason
import sttp.ai.core.agent.mcp.McpTools
import sttp.client4.DefaultSyncBackend
import sttp.model.Uri.UriContext
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity
import sttp.tapir.Schema
import sttp.tapir.server.netty.sync.NettySyncServer

class ParallelSearchAgentExampleSpec extends AnyFlatSpec with Matchers {
  private given MonadError[Identity] = IdentityMonad

  case class SearchInput(objective: String, search_queries: Vector[String], session_id: String) derives Codec, Schema
  case class FetchInput(urls: Vector[String], objective: String, session_id: String) derives Codec, Schema

  private val documentationUrl = "https://sttp-ai.softwaremill.com/agents/result-dependent-fixture.html"

  private def withTools(searchUrl: String)(check: Seq[sttp.ai.core.agent.AgentTool[Identity, ?]] => Unit): Unit = supervised {
    var searchSession: Option[String] = None
    val search = tool("web_search").input[SearchInput].handle { in =>
      in.search_queries should not be empty
      searchSession = Some(in.session_id)
      ToolResult.text(Json.obj("results" -> Json.arr(Json.obj("url" -> Json.fromString(searchUrl)))).noSpaces)
    }
    val fetch = tool("web_fetch").input[FetchInput].handle { in =>
      in.urls shouldBe Vector(searchUrl)
      Some(in.session_id) shouldBe searchSession
      ToolResult.text(
        Json
          .obj(
            "results" -> Json
              .arr(Json.obj("excerpts" -> Json.arr(Json.fromString("Loaded by McpTools.fromClient: unique fixture excerpt")))),
            "errors" -> Json.arr()
          )
          .noSpaces
      )
    }
    val binding = NettySyncServer().port(0).addEndpoint(McpServer(tools = List(search, fetch)).endpoint(List("mcp"))).start()
    try {
      val backend = DefaultSyncBackend()
      try {
        val client = McpClient[Identity](
          ClientHttpTransport[Identity](backend, uri"http://localhost:${binding.port}/mcp"),
          Implementation("sttp-ai-parallel-test", "0.0.1")
        )
        try check(McpTools.fromClient(client, namePrefix = Some("parallel")))
        finally client.close()
      } finally backend.close()
    } finally binding.stop()
  }

  "The controlled Parallel example" should "dispatch loaded HTTP MCP tools through the OpenAI loop and feed results into later turns" in
    withTools(documentationUrl) { tools =>
      val result = ParallelSearchAgentExample.run(tools)
      result.toolCalls.map(_.toolName) shouldBe Seq("parallel_web_search", "parallel_web_fetch")
      result.toolCalls.map(_.id) shouldBe Seq("search", "fetch")
      result.iterations shouldBe 3
      result.finishReason shouldBe FinishReason.NaturalStop
      result.finalAnswer shouldBe Right("Loaded by McpTools.fromClient: unique fixture excerpt")
    }

  it should "fail if search does not return the documentation URL rather than fetch a hardcoded fallback" in
    withTools("https://example.com/unrelated") { tools =>
      val error = intercept[IllegalStateException](ParallelSearchAgentExample.run(tools))
      error.getMessage should include("Search did not return an sttp-ai documentation URL")
    }
}
