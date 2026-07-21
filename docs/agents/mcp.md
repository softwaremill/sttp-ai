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

## Lifecycle

* You own the client: keep it open while the agent runs, and close it afterwards.
* The tool list is a snapshot taken by `fromClient`; `tools/list_changed` notifications are not observed.

## Tool names

MCP allows names with dots, slashes, non-ASCII characters, and up to 128 characters. OpenAI's function calling
requires `^[a-zA-Z0-9_-]{1,64}$` and rejects the whole request otherwise, so `fromClient` adapts names in two steps:

* **`namePrefix`** (optional) is applied first: with `Some("mcp")`, a server tool `add` is exposed as `mcp_add`.
  Use it to keep tools from different sources — other MCP servers, or manually defined tools — distinct from
  each other.
* **Sanitization** then rewrites the (possibly prefixed) name to `[A-Za-z0-9_-]`, truncated to 64 characters. The
  server always receives the tool's original, unprefixed, unsanitized name in `tools/call`.

**Duplicate detection.** If two tools from the *same* `fromClient` call end up with the same exposed name — a
sanitization collision, or a server reusing one name for genuinely different tools — `fromClient` fails with
`McpToolConversionException` naming every collision, instead of silently routing calls to the wrong tool. An MCP
server re-listing the exact same tool across pages is deduplicated instead (even if its free-form `_meta` field
varies); a genuinely different definition under the same name still fails. `namePrefix` cannot fix a collision
reported this way — it is applied identically to every tool in one `fromClient` call, so it can never separate
two names that already collide; rename one of them on the server instead.

This check does not extend across multiple `fromClient` calls or to manually defined tools: an agent looks tools
up by name, so if you combine tools from several sources without keeping their exposed names distinct yourself
(with `namePrefix`), the one loaded last silently shadows any earlier tool with the same name — no error is
raised.

## Results and errors

* Results are rendered as text: text content blocks are joined with newlines, other block types as compact JSON.
* Results the server marks as errors are returned to the LLM prefixed with `Tool execution failed:`.
* Transport failures surface as exceptions, subject to the agent's configured `ExceptionHandler`.
* Before a tool call reaches the server, arguments that are JSON `null` are dropped for parameters the server's
  schema does not list as `required` (the server then sees them as simply missing, not explicitly `null`); nulls
  for `required` parameters — including ones strict-mode normalization made nullable — are left in place.

## Backend caveats

* The OpenAI agent backend registers tools with `strict: true` function calling by default, normalizing schemas to
  strict-mode rules: `additionalProperties: false` on objects, all properties listed as `required`, and
  originally-optional properties made nullable (the model passes `null` for them instead of omitting them). If a
  schema uses JSON Schema features strict mode cannot accept, the API rejects it at request time — pass
  `strictTools = false` to the builder as an escape hatch:

  ```scala
  OpenAIAgent.synchronous(OpenAI.fromEnv, "gpt-4o-mini", strictTools = false)
  ```

* The Claude agent backend passes the server's original tool schema JSON through untouched — nested objects, arrays,
  enums, `required` lists, and any other JSON Schema keywords are all forwarded as received. The only modification is
  adding a top-level `"type": "object"` when the schema omits it (Anthropic requires `input_schema.type == "object"`,
  but MCP allows tools to omit `type`, e.g. `{}` for a no-argument tool).
