# MCP tools

Instead of defining every tool by hand with `AgentTool.fromFunction`, agents can load their tools from
[Model Context Protocol](https://modelcontextprotocol.io) (MCP) servers. The `mcp` module integrates
[chimp](https://github.com/softwaremill/chimp)'s MCP client: it discovers the server's tools (following
`tools/list` pagination), converts each tool's JSON Schema, and executes calls remotely via `tools/call`.

The module is available for Scala 3 only (chimp is Scala 3 only):

```scala
"com.softwaremill.sttp.ai" %% "mcp" % "@VERSION@"
```

First create and initialize a chimp `McpClient` using any of its transports — the stdio transport launches
the server as a subprocess and is synchronous (`F = Identity`); the HTTP transport works with any sttp
`Backend[F]`. Then `McpTools.fromClient` turns the server's tools into regular agent tools:

```scala mdoc:compile-only
import chimp.client.McpClient
import chimp.client.transport.ClientStdioTransport
import chimp.protocol.Implementation
import sttp.ai.core.agent.mcp.McpTools
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.client4.DefaultSyncBackend
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity

given MonadError[Identity] = IdentityMonad

// launches the MCP server as a subprocess, speaking JSON-RPC over stdin/stdout
val transport = ClientStdioTransport(List("npx", "-y", "@modelcontextprotocol/server-everything"))
val client = McpClient[Identity](transport, Implementation("sttp-ai-agent", "1.0.0"))

val backend = DefaultSyncBackend()

try
  val mcpTools = McpTools.fromClient(client, namePrefix = Some("mcp"))

  val agent = OpenAIAgent
    .synchronous(OpenAI.fromEnv, "gpt-4o-mini")
    .maxIterations(10)
    .tools(mcpTools)
    .build

  val result = agent.run("Add 2 and 3 using the available tools")(backend)
  println(result.finalAnswer)
finally
  backend.close()
  client.close()
```

Notes:

* You own the client's lifecycle: it must stay open while the agent runs, and you close it afterwards.
* The tool list is a snapshot taken by `fromClient`; `tools/list_changed` notifications are not observed.
* `namePrefix` is optional — with `Some("mcp")`, a server tool `add` is exposed to the LLM as `mcp_add`
  (the original name is still used when calling the server). Use it to avoid name collisions with manual
  tools or tools from other MCP servers.
* Results are rendered as text for the agent loop: text content blocks are joined with newlines, other
  block types are rendered as compact JSON, and results the server marks as errors are returned to the
  LLM prefixed with `Tool execution failed:`. Transport failures surface as exceptions and go through the
  agent's configured `ExceptionHandler`.

## Backend caveats

The LLM backends impose their own constraints on tool schemas, which arbitrary MCP servers may not satisfy:

* The OpenAI agent backend registers tools with `strict: true` function calling, which requires every object in the
  schema to set `additionalProperties: false` and list all properties as `required`. MCP tools whose schemas don't
  conform are rejected by the OpenAI API at request time.
* The Claude agent backend converts tool schemas to a flat property list, so nested object structure in an MCP tool's
  input schema is not conveyed to the model.

If you hit either limitation with a particular MCP server, consider wrapping the problematic tool manually with
`AgentTool.fromFunction` for now.
