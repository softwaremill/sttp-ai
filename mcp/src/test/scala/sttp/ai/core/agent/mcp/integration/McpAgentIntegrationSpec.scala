package sttp.ai.core.agent.mcp.integration

import chimp.client.McpClient
import chimp.client.transport.ClientHttpTransport
import chimp.protocol.Implementation
import chimp.server.{tool, McpServer, ToolResult}
import io.circe.Codec
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.supervised
import sttp.ai.claude.agent.ClaudeAgent
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.core.agent.mcp.McpTools
import sttp.ai.core.agent.{AgentBuilder, AgentResult, AgentTool}
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.client4.DefaultSyncBackend
import sttp.model.Uri.UriContext
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity
import sttp.tapir.Schema
import sttp.tapir.server.netty.sync.NettySyncServer

/** Agent-level integration tests: an LLM drives tools loaded from a real (in-process) MCP server whose tool input has a nested object and
  * an optional parameter — the two schema shapes that previously degraded per backend. Each test self-cancels without its API key.
  */
class McpAgentIntegrationSpec extends AnyFlatSpec with Matchers {

  private given MonadError[Identity] = IdentityMonad

  case class Location(lat: Double, lng: Double) derives Codec, Schema
  case class CreateEventInput(title: String, location: Location, priority: Option[String]) derives Codec, Schema

  private def withMcpAgentRun(builderFor: Seq[AgentTool[Identity, ?]] => AgentBuilder[Identity])(
      check: AgentResult[String] => Assertion
  ): Assertion =
    supervised {
      val createEvent = tool("create-event")
        .description("Creates an event with a title at the given location; priority is optional")
        .input[CreateEventInput]
        .handle(in =>
          ToolResult.text(s"Created '${in.title}' at (${in.location.lat}, ${in.location.lng}), priority=${in.priority.getOrElse("normal")}")
        )
      val binding = NettySyncServer().port(0).addEndpoint(McpServer(tools = List(createEvent)).endpoint(List("mcp"))).start()
      try {
        val backend = DefaultSyncBackend()
        try {
          val transport = ClientHttpTransport[Identity](backend, uri"http://localhost:${binding.port}/mcp")
          val client = McpClient[Identity](transport, Implementation("sttp-ai-mcp-it", "0.0.1"))
          try {
            val tools = McpTools.fromClient(client)
            val agent = builderFor(tools).maxIterations(5).build
            val result = agent.run(
              "Create an event titled 'Standup' at latitude 1.5 and longitude 2.5. Do not set a priority. Then report the tool's answer."
            )(backend)
            check(result)
          } finally client.close()
        } finally backend.close()
      } finally binding.stop()
    }

  private def assertToolExecuted(result: AgentResult[String]): Assertion = {
    result.toolCalls.map(_.toolName) should contain("create-event")
    val output = result.toolCalls.filter(_.toolName == "create-event").map(_.output).mkString("\n")
    output should include("Standup")
    output should include("1.5")
    output should include("2.5")
  }

  "an OpenAI agent" should "call an MCP tool with a nested and optional-parameter schema (strict mode)" in {
    if (sys.env.get("OPENAI_API_KEY").forall(_.isEmpty)) cancel("OPENAI_API_KEY not defined - skipping integration test")
    withMcpAgentRun(tools => OpenAIAgent.synchronous(OpenAI.fromEnv, "gpt-4o-mini").tools(tools))(assertToolExecuted)
  }

  "a Claude agent" should "call an MCP tool with a nested and optional-parameter schema" in {
    if (sys.env.get("ANTHROPIC_API_KEY").forall(_.isEmpty)) cancel("ANTHROPIC_API_KEY not defined - skipping integration test")
    withMcpAgentRun(tools => ClaudeAgent.synchronous(ClaudeConfig.fromEnv, "claude-haiku-4-5-20251001").tools(tools))(assertToolExecuted)
  }
}
