package sttp.ai.core.agent.mcp

import chimp.client.McpClient
import chimp.client.transport.ClientHttpTransport
import chimp.protocol.Implementation
import chimp.server.{tool, McpServer, ToolResult}
import io.circe.{Codec, Json}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.supervised
import sttp.client4.DefaultSyncBackend
import sttp.model.Uri.UriContext
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity
import sttp.tapir.Schema
import sttp.tapir.server.netty.sync.NettySyncServer

class McpToolsHttpServerSpec extends AnyFlatSpec with Matchers {

  private given MonadError[Identity] = IdentityMonad

  case class AddInput(a: Int, b: Int) derives Codec, Schema

  it should "discover and execute tools from a chimp MCP server over HTTP" in
    supervised {
      val adder = tool("adder").description("Adds two numbers").input[AddInput].handle(in => ToolResult.text(s"${in.a + in.b}"))
      val binding = NettySyncServer().port(0).addEndpoint(McpServer(tools = List(adder)).endpoint(List("mcp"))).start()
      try {
        val backend = DefaultSyncBackend()
        try {
          val transport = ClientHttpTransport[Identity](backend, uri"http://localhost:${binding.port}/mcp")
          val client = McpClient[Identity](transport, Implementation("sttp-ai-mcp-test", "0.0.1"))
          try {
            val tools = McpTools.fromClient(client)
            tools.map(_.name) shouldBe Seq("adder")
            tools.head.description shouldBe "Adds two numbers"
            tools.head.jsonSchema.properties.keySet shouldBe Set("a", "b")
            tools.head.execute(Map("a" -> Json.fromInt(2), "b" -> Json.fromInt(3))) shouldBe "5"
          } finally client.close()
        } finally backend.close()
      } finally binding.stop()
    }
}
