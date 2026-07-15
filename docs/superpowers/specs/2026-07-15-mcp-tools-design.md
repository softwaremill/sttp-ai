# MCP Server Integration for sttp-ai Agents — Design

**Date:** 2026-07-15
**Status:** Approved

## Problem

All agent tools must currently be defined by hand with `AgentTool.fromFunction` /
`AgentTool.dynamicF` — name, description, schema, and execution logic are boilerplate
the developer writes per tool. MCP-compliant servers already expose exactly this
metadata (tool discovery, JSON Schema, remote execution), so agents should be able to
load their tools from an MCP server instead.

## Goals (v1 scope)

- Discover tools from an MCP server (all pages of `tools/list`).
- Convert each MCP tool definition (name, description, JSON Schema) into an
  `AgentTool` usable in `AgentConfig.userTools`.
- Execute tool calls remotely via `tools/call` and return results to the agent loop.
- Support every transport chimp supports (stdio, HTTP, streaming variants) without
  transport-specific code in sttp-ai.

## Non-goals (v1)

- `AgentConfig.fromMcpServer("stdio://...")`-style URI string constructors — lossy
  (cannot express env vars, working dir, sttp `Backend[F]`, auth headers, effect
  type) and hides client lifecycle. Convenience shortcuts can be layered on later.
- MCP prompts and resources — no consumer in the current agent loop.
- Dynamic tool-list refresh via `tools/list_changed` notifications — the tool list is
  a snapshot taken at discovery time.
- Scala 2.13 support — chimp is Scala 3 only, which makes this impossible for now.

## Architecture

### Dependency: chimp MCP client

The integration uses [chimp](https://github.com/softwaremill/chimp)
(`com.softwaremill.chimp::client`, latest release — 0.4.0 at time of writing).
chimp's `McpClient[F]` provides `listTools(cursor)` and
`callTool(name, arguments: Json): F[CallToolResult]`, and is built on sttp's
`MonadError[F]` — the same effect abstraction sttp-ai's `Agent` uses, so no adapter
layer is needed. Transports: `ClientStdioTransport` (sync, `Identity`),
`ClientHttpTransport[F](backend: Backend[F])`, plus streaming variants.

### New module: `mcp`

A new sbt-projectmatrix project `mcp` at `mcp/`, Scala 3 only
(`jvmPlatform(scalaVersions = scala3)`, same pattern as `streaming/ox`), added to
`allAgregates`. Dependencies: `core` (matrix dependency) +
`com.softwaremill.chimp::client`. **No changes to `core`** — the integration is pure
glue over the existing `AgentTool.dynamicF`.

### Public API

One object, `mcp/src/main/scala/sttp/ai/core/agent/mcp/McpTools.scala`:

```scala
package sttp.ai.core.agent.mcp

object McpTools:
  /** Discovers all tools from an initialized MCP client (following pagination)
    * and adapts them as agent tools. The caller owns the client: it must stay
    * open while the agent runs, and be closed afterwards.
    *
    * @param namePrefix
    *   When set, tools are exposed to the LLM as s"${prefix}_${name}" to avoid
    *   collisions with manual tools or other MCP servers; the original name is
    *   still used when calling the server.
    */
  def fromClient[F[_]](
      client: McpClient[F],
      namePrefix: Option[String] = None
  )(using MonadError[F]): F[Seq[AgentTool[F, Map[String, Json]]]]
```

Usage (stdio transport is synchronous, so here `F = Identity` and the effects
disappear; with an effectful `F` — e.g. `ClientHttpTransport` over a cats-effect
backend — the `McpClient(...)` and `fromClient(...)` steps are sequenced with
`flatMap`/for-comprehension):

```scala
val transport = ClientStdioTransport(List("npx", "-y", "my-mcp-server"))
val client = McpClient(transport, Implementation("sttp-ai-agent", "1.0"))
val mcpTools = McpTools.fromClient(client)
val config = AgentConfig(userTools = mcpTools ++ myManualTools)
// ... run agent ...
client.close()
```

The `using MonadError[F]` sequences the paginated `listTools` calls; it is the same
type class users already have from constructing the transport and agent.

## Data flow

### Discovery & conversion

For each `ToolDefinition` returned by `listTools` (looping on `nextCursor` until
exhausted):

| AgentTool field | Source |
|---|---|
| `name` | `namePrefix.fold(t.name)(p => s"${p}_${t.name}")`; adapter closes over original `t.name` for `callTool` |
| `description` | `t.description.orElse(t.title).getOrElse(t.name)` (AgentTool's description is non-optional) |
| `jsonSchema` | `t.inputSchema: Json` decoded to `sttp.apispec.Schema` via the `jsonschema-circe` decoders already on core's classpath |
| `execute` | `Map[String, Json]` input → `Json.fromFields` → `client.callTool(originalName, argsJson)` → rendered to `String` |

The adapter is built with the existing `AgentTool.dynamicF[F]` — no new `AgentTool`
variants.

A tool whose `inputSchema` fails to decode into `sttp.apispec.Schema` fails the
whole `fromClient` call with a descriptive exception (fail fast at discovery, rather
than an agent silently missing a tool).

### Result rendering (`CallToolResult` → `String`)

1. All `ToolContent.Text` blocks joined with `"\n"`.
2. Non-text blocks (image / audio / resource / resource_link) are rendered as their
   compact JSON encoding (chimp's encoders), so URIs and metadata reach the LLM
   instead of being dropped.
3. Only when the content list is empty entirely: fall back to
   `structuredContent.noSpaces` (or the empty string if that is absent too).
4. `isError = true`: return the rendered content prefixed with
   `"Tool execution failed: "`. MCP carries the human-readable error in `content`;
   returning it to the model (instead of throwing) matches how the agent loop treats
   tool errors as conversation input.

### Error handling

- Transport/protocol failures (`McpTimeoutException`, connection death, JSON-RPC
  errors) surface as exceptions thrown by `callTool` inside `execute`, so they flow
  through the user-configured `ExceptionHandler` exactly like any other tool
  exception. No special casing in the MCP module.
- Discovery-time failures (unreachable server, undecodable schema) fail the
  `fromClient` effect.

### Lifecycle

`fromClient` never closes the client. The caller owns it: the client must outlive
the agent run and be closed by the caller afterwards. Stated in scaladoc and docs.

## Testing

1. **Unit tests** (`mcp/src/test`, plain `sbt test`): hand-written fake
   `McpClient[Identity]` covering:
   - pagination across multiple `listTools` pages;
   - prefix applied to the LLM-facing name while `callTool` receives the original;
   - description fallback chain (description → title → name);
   - schema decode failure fails `fromClient` with a useful message;
   - result rendering: text joining, `structuredContent` fallback, non-text blocks
     as JSON, `isError` prefix.
2. **In-process integration test**: chimp `server` module as a Test-scope
   dependency hosting a real MCP server with a sample tool over HTTP;
   `McpTools.fromClient` connects via `ClientHttpTransport` and executes the tool.
   Verifies wire-level protocol compatibility on every `sbt test` run — no API keys
   or containers.
3. **Agent-level integration** (optional, existing `*IntegrationSpec` pattern): an
   agent using MCP-loaded tools against a real LLM API, auto-skipped when
   `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` are absent.

## Documentation & examples

- Compile-checked mdoc page `docs/agents/mcp.md` (added to the docs toc): stdio MCP
  server tools plugged into an agent, the lifecycle rule, the `namePrefix` option,
  and the Scala-3-only caveat. (Originally a runnable scala-cli example in
  `examples/` was planned, but CI's `verifyExamplesCompileUsingScalaCli` compiles
  examples against published artifacts only — impossible before the `mcp` artifact's
  first release. A runnable example can be added post-release.)

## Development workflow

Per CLAUDE.md: `sbt scalafmtAll` after every change, `sbt compile`, module tests,
commit per phase. If the required chimp version is not yet on Maven Central,
`publishLocal` chimp from `/Users/adamrybicki/SML/chimp` during development and note
the version pin.
