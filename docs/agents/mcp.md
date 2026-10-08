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
  result.finalAnswer match {
    case Right(answer) => println(s"Answer: $answer")
    case Left(failure) => println(s"Agent did not finish cleanly: $failure")
  }
finally
  backend.close()
  client.close()
```

The same tools work with the Claude backend — here's the equivalent, complete example:

```scala mdoc:compile-only
import chimp.client.McpClient
import chimp.client.transport.ClientStdioTransport
import chimp.protocol.Implementation
import sttp.ai.claude.agent.ClaudeAgent
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.ClaudeModel
import sttp.ai.core.agent.mcp.McpTools
import sttp.client4.DefaultSyncBackend
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity

given MonadError[Identity] = IdentityMonad

val claudeTransport = ClientStdioTransport(List("npx", "-y", "@modelcontextprotocol/server-everything"))
val claudeClient = McpClient[Identity](claudeTransport, Implementation("sttp-ai-agent", "1.0.0"))

val claudeBackend = DefaultSyncBackend()

try
  val mcpTools = McpTools.fromClient(claudeClient, namePrefix = Some("mcp"))

  val agent = ClaudeAgent
    .synchronous(ClaudeConfig.fromEnv, ClaudeModel.ClaudeHaiku4_5.value)
    .maxIterations(10)
    .tools(mcpTools)
    .build

  val result = agent.run("Add 2 and 3 using the available tools")(claudeBackend)
  result.finalAnswer match {
    case Right(answer) => println(s"Answer: $answer")
    case Left(failure) => println(s"Agent did not finish cleanly: $failure")
  }
finally
  claudeBackend.close()
  claudeClient.close()
```

## HTTP example: Parallel Search

[Parallel Search MCP](https://docs.parallel.ai/integrations/mcp/search-mcp) exposes `web_search` and
`web_fetch` over Streamable HTTP at `https://search.parallel.ai/mcp`. Its anonymous free tier needs no
Parallel API key and is intended for exploration and light use; rate limits apply.

The runnable [ParallelSearchAgentExample](https://github.com/softwaremill/sttp-ai/blob/master/examples/src/main/scala/examples/ParallelSearchAgentExample.scala)
loads both tools with `McpTools.fromClient`, registers them with `OpenAIAgent`, and runs the native agent
loop. Save the linked file locally and run:

```bash
scala-cli run ParallelSearchAgentExample.scala --server=false
```

The file declares Scala 3, JDK 21, and its `mcp` and `openai` dependencies. It makes real anonymous MCP
requests with a project `User-Agent`; no Parallel key or model credentials are needed. The client and
HTTP backend stay open until the loop finishes and are closed afterwards.

Only the model HTTP responses are controlled using sttp's stub backend. The first response requests
search; the second reads the returned tool message and selects the MCP documentation URL from the
search results before requesting fetch. The third reads the fetch tool message and returns its excerpts.
Assertions check both loaded tool names, useful fetched content, and three model turns. Unexpected
results fail the example. This demonstrates tool dispatch and result feedback, not production model
quality or OpenAI API acceptance of the schemas.

To use a real model, keep the same HTTP client and loaded tools and run them with a real model backend:

```scala mdoc:compile-only
import chimp.client.McpClient
import chimp.client.transport.ClientHttpTransport
import chimp.protocol.Implementation
import sttp.ai.core.agent.mcp.McpTools
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.client4.DefaultSyncBackend
import sttp.model.Header
import sttp.model.Uri.UriContext
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity

given MonadError[Identity] = IdentityMonad

val parallelBackend = DefaultSyncBackend()
try {
  val transport = ClientHttpTransport[Identity](
    parallelBackend,
    uri"https://search.parallel.ai/mcp",
    headers = Seq(Header("User-Agent", "sttp-ai-parallel-example/@VERSION@"))
  )
  val client = McpClient[Identity](transport, Implementation("sttp-ai-parallel-example", "@VERSION@"))
  try {
    val tools = McpTools.fromClient(client, namePrefix = Some("parallel"))
    val agent = OpenAIAgent.synchronous(OpenAI.fromEnv, "gpt-4o-mini").maxIterations(4).tools(tools).build
    println(agent.run("Search for sttp-ai's MCP documentation, fetch its URL, then summarize the excerpts.")(parallelBackend).finalAnswer)
  } finally client.close()
} finally parallelBackend.close()
```

This variant also requires the `openai` module at the same version, `OPENAI_API_KEY`, and incurs model inference costs. The controlled example does not
validate a live model's tool choices; the default strict-tool behavior and backend caveats below still apply.

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

`fromClient` also fails with `McpToolConversionException` if a tool's input schema is not valid JSON Schema and
cannot be decoded, naming the offending tool.

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
