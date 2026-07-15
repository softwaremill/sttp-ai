# MCP Tools Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let sttp-ai agents load their tools from MCP servers via chimp's MCP client, instead of hand-writing every `AgentTool`.

**Architecture:** A new Scala-3-only `mcp` module containing one object, `McpTools`, that discovers tools from a chimp `McpClient[F]` (paginated `tools/list`), converts each tool definition into an `AgentTool` via the existing `AgentTool.dynamicF`, and executes calls remotely through `tools/call`. No changes to the cross-built `core` module.

**Tech Stack:** Scala 3.3.8, sbt-projectmatrix, chimp 0.4.0 (`chimp-client`, and `chimp-server` for tests), circe, sttp-apispec (`jsonschema-circe`), scalatest, tapir netty-sync server (tests only).

**Spec:** `docs/superpowers/specs/2026-07-15-mcp-tools-design.md`

## Global Constraints

- Work on branch `mcp-tools-integration` (already created; spec is committed there).
- New module is Scala 3 only: `jvmPlatform(scalaVersions = scala3)` where `scala3 = List("3.3.8")` in `build.sbt`. Do NOT touch `core`'s sources or its cross-build setup.
- chimp version: `0.4.0`, org `com.softwaremill.chimp`, artifacts `chimp-client` and `chimp-server` (both on Maven Central — chimp's own examples reference them).
- Production code package: `sttp.ai.core.agent.mcp`.
- **Run `sbt scalafmtAll` after EVERY code change** (CLAUDE.md requirement; CI fails otherwise). Scala 3 syntax (`import x.*`), max column 140.
- sbt-projectmatrix suffixes Scala 3 project rows: the new module's sbt project id is expected to be `mcp3` (like `ox3` for `streaming/ox`). If `sbt mcp3/compile` reports no such project, run `sbt projects` and use the listed id (`mcp` or `mcp3`) consistently everywhere below.
- Deviation from the spec, discovered during planning: the spec asks for a runnable scala-cli example in `examples/`. CI runs `sbt verifyExamplesCompileUsingScalaCli`, which compiles every `examples/**.scala` with scala-cli against **published** artifacts — and the `mcp` artifact is not published yet, so such an example would break CI. Instead, Task 5 adds a compile-checked mdoc documentation page (`docs/agents/mcp.md`); a runnable scala-cli example can be added after the first release that includes the `mcp` artifact.

---

### Task 1: Build scaffolding — `mcp` module and chimp dependencies

**Files:**
- Modify: `project/Dependencies.scala`
- Modify: `build.sbt`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: sbt project `mcp3` (base name `mcp`) with `chimp-client` on Compile, `chimp-server` + `tapir-netty-server-sync` + scalatest on Test, depending on `core % "compile->compile;test->test"`. Later tasks compile/test with `sbt mcp3/...`.

- [ ] **Step 1: Add chimp version and library entries to `project/Dependencies.scala`**

In `object V`, after `val circeGenericExtras = ...`, add:

```scala
    val chimp = "0.4.0"
```

In `object Libraries`, after `val tapirApispecDocs = ...`, add (plain `%%` — the module is JVM-only, Scala 3 only, so no `Def.setting`/`%%%` needed, following the `sttpClientOx` style):

```scala
    val chimpClient = "com.softwaremill.chimp" %% "chimp-client" % V.chimp

    // Test-only: an in-process MCP server for wire-level integration tests of the mcp module
    val chimpServer = "com.softwaremill.chimp" %% "chimp-server" % V.chimp % Test
    val tapirNettyServerSync = "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % V.tapir % Test
```

- [ ] **Step 2: Add the `mcp` project to `build.sbt`**

After the `lazy val ox = ...` definition, add:

```scala
lazy val mcp = (projectMatrix in file("mcp"))
  .jvmPlatform(
    scalaVersions = scala3
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Libraries.chimpClient,
      Libraries.chimpServer,
      Libraries.tapirNettyServerSync,
      Libraries.scalaTest.value
    )
  )
  .dependsOn(core % "compile->compile;test->test")
```

In `lazy val allAgregates`, add a row after `ox.projectRefs ++`:

```scala
  mcp.projectRefs ++
```

- [ ] **Step 3: Verify the empty module compiles**

Run: `sbt mcp3/compile` (fall back to the id shown by `sbt projects` — see Global Constraints)
Expected: `[success]` — resolves chimp 0.4.0 from Maven Central and compiles an empty module.

- [ ] **Step 4: Format and commit**

```bash
sbt scalafmtAll
git add project/Dependencies.scala build.sbt
git commit -m "Add mcp module scaffolding with chimp dependencies"
```

---

### Task 2: `McpTools.renderResult` — CallToolResult → String (TDD)

**Files:**
- Create: `mcp/src/main/scala/sttp/ai/core/agent/mcp/McpTools.scala`
- Create: `mcp/src/test/scala/sttp/ai/core/agent/mcp/McpToolsSpec.scala`

**Interfaces:**
- Consumes: chimp protocol types `CallToolResult`, `ToolContent` (from `chimp.protocol`).
- Produces: `McpTools.renderResult(result: CallToolResult): String`, visibility `private[mcp]` (unit-testable, not public API). Task 3 adds `fromClient` to the same object and calls `renderResult`.

Rendering rules (from the spec):
1. Content blocks are rendered in order: `ToolContent.Text` as its text, any other block as its compact JSON encoding (chimp provides `Encoder[ToolContent]`), joined with `"\n"`.
2. If there are no content blocks at all, fall back to `structuredContent.noSpaces` (empty string when absent too).
3. If `isError` is true, prefix the rendered body with `"Tool execution failed: "`.

- [ ] **Step 1: Write the failing tests**

Create `mcp/src/test/scala/sttp/ai/core/agent/mcp/McpToolsSpec.scala`:

```scala
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `sbt "mcp3/testOnly sttp.ai.core.agent.mcp.McpToolsSpec"`
Expected: FAIL to compile with "value McpTools is not a member" / "Not found: McpTools".

- [ ] **Step 3: Implement `McpTools.renderResult`**

Create `mcp/src/main/scala/sttp/ai/core/agent/mcp/McpTools.scala`:

```scala
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `sbt "mcp3/testOnly sttp.ai.core.agent.mcp.McpToolsSpec"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Format and commit**

```bash
sbt scalafmtAll
git add mcp/
git commit -m "Render MCP CallToolResult as agent tool output"
```

---

### Task 3: `McpTools.fromClient` — discovery, conversion, execution (TDD)

**Files:**
- Create: `mcp/src/test/scala/sttp/ai/core/agent/mcp/FakeMcpClient.scala`
- Modify: `mcp/src/main/scala/sttp/ai/core/agent/mcp/McpTools.scala`
- Modify: `mcp/src/test/scala/sttp/ai/core/agent/mcp/McpToolsSpec.scala`

**Interfaces:**
- Consumes: `McpTools.renderResult` (Task 2); chimp's `McpClient[F]` (`listTools(cursor: Option[Cursor]): F[ListToolsResponse]`, `callTool(name: String, arguments: Json): F[CallToolResult]`); sttp-ai's `AgentTool.dynamicF[F](name, description, schema)(f: Map[String, Json] => F[String])`; `sttp.apispec.circe.*` for `Decoder[sttp.apispec.Schema]`.
- Produces the public API of the module:
  - `McpTools.fromClient[F[_]](client: McpClient[F], namePrefix: Option[String] = None)(using MonadError[F]): F[Seq[AgentTool[F, Map[String, Json]]]]`
  - `class McpToolConversionException(message: String, cause: Throwable) extends RuntimeException`
  - Test helper `FakeMcpClient` (used again conceptually by reviewers; integration test in Task 4 uses a real server instead).

- [ ] **Step 1: Write the fake MCP client test helper**

Create `mcp/src/test/scala/sttp/ai/core/agent/mcp/FakeMcpClient.scala`. Note `Cursor` is `type Cursor = String` in chimp; the fake interprets a cursor as an index into `pages`. `Identity[A] = A`, so members are written with plain result types. All members not used by `McpTools` throw.

```scala
package sttp.ai.core.agent.mcp

import chimp.client.McpClient
import chimp.protocol.*
import io.circe.Json
import sttp.shared.Identity

/** In-memory MCP client stub: serves canned `tools/list` pages and `tools/call` results, and records invocations. A `nextCursor` in a
  * page must be the (string) index of the next page in `pages`.
  */
class FakeMcpClient(
    pages: Seq[ListToolsResponse],
    callResults: Map[String, CallToolResult] = Map.empty
) extends McpClient[Identity] {
  var recordedCalls: Vector[(String, Json)] = Vector.empty
  var recordedCursors: Vector[Option[Cursor]] = Vector.empty

  override def listTools(cursor: Option[Cursor]): ListToolsResponse = {
    recordedCursors = recordedCursors :+ cursor
    pages(cursor.fold(0)(_.toInt))
  }

  override def callTool(name: String, arguments: Json): CallToolResult = {
    recordedCalls = recordedCalls :+ (name -> arguments)
    callResults.getOrElse(name, CallToolResult(List(ToolContent.Text(text = s"no canned result for $name"))))
  }

  override def ping(): Unit = ()
  override def close(): Unit = ()
  override def serverCapabilities: ServerCapabilities = ServerCapabilities()
  override def serverInfo: Implementation = Implementation("fake-server", "0.0.1")
  override def listPrompts(cursor: Option[Cursor]): ListPromptsResult = unsupported
  override def getPrompt(name: String, arguments: Map[String, String]): GetPromptResult = unsupported
  override def listResources(cursor: Option[Cursor]): ListResourcesResult = unsupported
  override def listResourceTemplates(cursor: Option[Cursor]): ListResourceTemplatesResult = unsupported
  override def readResource(uri: String): ReadResourceResult = unsupported
  override def complete(ref: CompleteRef, argument: CompleteArgument): CompleteResult = unsupported
  override def setLoggingLevel(level: LoggingLevel): Unit = unsupported
  override def sendProgress(token: ProgressToken, progress: Double, total: Option[Double], message: Option[String]): Unit = unsupported

  private def unsupported: Nothing = throw new UnsupportedOperationException("not used by McpTools")
}
```

(If the compiler reports a missing or mismatched member, adjust to the exact signatures in chimp's `McpClient` trait — `chimp/client/src/main/scala/chimp/client/McpClient.scala` in the chimp repo, also on the classpath as a library. Default arguments in the trait do not need repeating in overrides.)

- [ ] **Step 2: Add the failing `fromClient` tests**

Append to `McpToolsSpec` (inside the class). Add these imports to the file's import list: `chimp.protocol.{ListToolsResponse, ToolDefinition}`, `sttp.monad.{IdentityMonad, MonadError}`, `sttp.shared.Identity`.

```scala
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
    val client = new FakeMcpClient(
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
    val client = new FakeMcpClient(
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
    val client = new FakeMcpClient(pages = Seq(ListToolsResponse(tools = List(toolDef("add")))))
    val tool = McpTools.fromClient(client).head
    tool.jsonSchema.properties.keySet shouldBe Set("a", "b")
  }

  it should "fail with a descriptive error when a tool's input schema cannot be decoded" in {
    val client = new FakeMcpClient(pages = Seq(ListToolsResponse(tools = List(toolDef("bad", schema = Json.fromString("nope"))))))
    val ex = the[McpToolConversionException] thrownBy McpTools.fromClient(client)
    ex.getMessage should include("bad")
  }

  it should "expose the prefixed name to the LLM but call the server with the original name" in {
    val client = new FakeMcpClient(
      pages = Seq(ListToolsResponse(tools = List(toolDef("add")))),
      callResults = Map("add" -> CallToolResult(List(ToolContent.Text(text = "3"))))
    )
    val tool = McpTools.fromClient(client, namePrefix = Some("calc")).head
    tool.name shouldBe "calc_add"
    tool.execute(Map("a" -> Json.fromInt(1), "b" -> Json.fromInt(2))) shouldBe "3"
    client.recordedCalls shouldBe Vector("add" -> Json.obj("a" -> Json.fromInt(1), "b" -> Json.fromInt(2)))
  }

  it should "keep original names when no prefix is given" in {
    val client = new FakeMcpClient(pages = Seq(ListToolsResponse(tools = List(toolDef("add")))))
    McpTools.fromClient(client).head.name shouldBe "add"
  }
```

- [ ] **Step 3: Run tests to verify the new ones fail**

Run: `sbt "mcp3/testOnly sttp.ai.core.agent.mcp.McpToolsSpec"`
Expected: FAIL to compile with "value fromClient is not a member of object ...McpTools" (and `McpToolConversionException` not found).

- [ ] **Step 4: Implement `fromClient`**

Replace `mcp/src/main/scala/sttp/ai/core/agent/mcp/McpTools.scala` with:

```scala
package sttp.ai.core.agent.mcp

import chimp.client.McpClient
import chimp.protocol.{CallToolResult, Cursor, ToolContent, ToolDefinition}
import io.circe.Json
import io.circe.syntax.*
import sttp.ai.core.agent.AgentTool
import sttp.apispec.Schema
import sttp.apispec.circe.*
import sttp.monad.MonadError
import sttp.monad.syntax.*

/** Thrown when a tool advertised by an MCP server cannot be converted into an [[sttp.ai.core.agent.AgentTool]] (e.g. its input schema is
  * not valid JSON Schema).
  */
class McpToolConversionException(message: String, cause: Throwable) extends RuntimeException(message, cause)

object McpTools {

  /** Discovers all tools exposed by an initialized MCP client (following `tools/list` pagination) and adapts each of them as an
    * [[sttp.ai.core.agent.AgentTool]], ready to be used in `AgentConfig.userTools`.
    *
    * The caller owns the client: it must stay open while the agent runs, and must be closed by the caller afterwards.
    *
    * Tool calls are executed remotely via `tools/call`. Results are rendered as text: text content blocks are joined with newlines, other
    * content blocks are rendered as compact JSON, and an empty content list falls back to the tool's structured content. Results marked as
    * errors by the server are prefixed with `"Tool execution failed: "` and returned to the LLM as regular tool output. Transport-level
    * failures surface as exceptions and are subject to the agent's configured `ExceptionHandler`.
    *
    * @param namePrefix
    *   When defined, tools are exposed to the LLM as `s"${prefix}_${name}"`, to avoid collisions with manually defined tools or tools
    *   loaded from other MCP servers. The original name is still used when calling the server.
    */
  def fromClient[F[_]](
      client: McpClient[F],
      namePrefix: Option[String] = None
  )(using monad: MonadError[F]): F[Seq[AgentTool[F, Map[String, Json]]]] =
    listAllTools(client, cursor = None, acc = Vector.empty).flatMap { definitions =>
      monad.eval(definitions.map(toAgentTool(client, _, namePrefix)))
    }

  private def listAllTools[F[_]](client: McpClient[F], cursor: Option[Cursor], acc: Vector[ToolDefinition])(using
      monad: MonadError[F]
  ): F[Vector[ToolDefinition]] =
    client.listTools(cursor).flatMap { response =>
      val all = acc ++ response.tools
      response.nextCursor match {
        case Some(next) => listAllTools(client, Some(next), all)
        case None       => monad.unit(all)
      }
    }

  private def toAgentTool[F[_]](client: McpClient[F], definition: ToolDefinition, namePrefix: Option[String])(using
      MonadError[F]
  ): AgentTool[F, Map[String, Json]] = {
    val schema = definition.inputSchema.as[Schema] match {
      case Right(s) => s
      case Left(error) =>
        throw new McpToolConversionException(s"Cannot decode the input schema of MCP tool '${definition.name}': ${error.getMessage}", error)
    }
    val exposedName = namePrefix.fold(definition.name)(prefix => s"${prefix}_${definition.name}")
    val description = definition.description.orElse(definition.title).getOrElse(definition.name)
    AgentTool.dynamicF(exposedName, description, schema) { input =>
      client.callTool(definition.name, Json.fromFields(input)).map(renderResult)
    }
  }

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
```

Implementation notes:
- `import sttp.apispec.circe.*` provides the `Decoder[Schema]` (`schemaDecoder`) — same codecs the `openai` module uses in `SchemaSupport.scala`.
- The schema-decode `throw` happens inside `monad.eval(...)`, so for effectful `F` it lands in the error channel; for `Identity` it throws directly (which the unit test asserts).
- `Json.fromFields(input)` builds the JSON object for `tools/call` from the `Map[String, Json]` that `AgentTool.dynamicF`'s codec produces.

- [ ] **Step 5: Run the full module test suite**

Run: `sbt mcp3/test`
Expected: PASS — all `McpToolsSpec` tests (11 total).

- [ ] **Step 6: Format and commit**

```bash
sbt scalafmtAll
git add mcp/
git commit -m "Load agent tools from MCP servers via chimp client"
```

---

### Task 4: In-process wire-level test against a real chimp MCP server

**Files:**
- Create: `mcp/src/test/scala/sttp/ai/core/agent/mcp/McpToolsHttpServerSpec.scala`

**Interfaces:**
- Consumes: `McpTools.fromClient` (Task 3); chimp server DSL (`chimp.server.{tool, McpServer, ToolResult}`); tapir `NettySyncServer` (Test-scope dep from Task 1).
- Produces: nothing for later tasks — this is a verification-only task. It runs in plain `sbt test` (sttp-ai "integration" tests are only gated by self-cancelling on missing API keys; this test needs none).

- [ ] **Step 1: Write the test**

Create `mcp/src/test/scala/sttp/ai/core/agent/mcp/McpToolsHttpServerSpec.scala`. This mirrors chimp's own `SyncHttpMcpServerSpec` setup: a netty-sync server on a random port inside an Ox `supervised` scope (ox is on the test classpath transitively via `tapir-netty-server-sync`), a synchronous HTTP transport, `F = Identity`.

```scala
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

  it should "discover and execute tools from a chimp MCP server over HTTP" in {
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
}
```

- [ ] **Step 2: Run the test**

Run: `sbt "mcp3/testOnly sttp.ai.core.agent.mcp.McpToolsHttpServerSpec"`
Expected: PASS. (Failure modes to watch for: netty port binding issues in sandboxed environments — the server binds only to localhost on an ephemeral port.)

- [ ] **Step 3: Run the whole module suite once more**

Run: `sbt mcp3/test`
Expected: PASS, `McpToolsSpec` + `McpToolsHttpServerSpec`.

- [ ] **Step 4: Format and commit**

```bash
sbt scalafmtAll
git add mcp/
git commit -m "Test MCP tool loading against an in-process chimp server"
```

---

### Task 5: Documentation page + final verification

**Files:**
- Create: `docs/agents/mcp.md`
- Modify: `docs/index.md` (toc: add `agents/mcp` after `agents/tools`, around line 44)
- Modify: `build.sbt` (docs module: add `mcp` to `.dependsOn(...)`)

**Interfaces:**
- Consumes: the public API from Task 3 (`McpTools.fromClient`), compile-checked by mdoc against the local `mcp` module.
- Produces: user-facing documentation; nothing consumed by other tasks.

- [ ] **Step 1: Add `mcp` to the docs module's dependencies**

In `build.sbt`, change the docs module's dependsOn from:

```scala
  .dependsOn(openai, fs2, zio, ox, pekko)
```

to:

```scala
  .dependsOn(openai, fs2, zio, ox, pekko, mcp)
```

- [ ] **Step 2: Write the documentation page**

Create `docs/agents/mcp.md`:

````markdown
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

try
  val mcpTools = McpTools.fromClient(client, namePrefix = Some("mcp"))

  val agent = OpenAIAgent
    .synchronous(OpenAI.fromEnv, "gpt-4o-mini")
    .maxIterations(10)
    .tools(mcpTools)
    .build

  val result = agent.run("Add 2 and 3 using the available tools")(DefaultSyncBackend())
  println(result.finalAnswer)
finally client.close()
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
````

(If mdoc fails on the `OpenAIAgent` builder calls, check the exact builder API in `docs/agents/configuration.md` and `core/src/main/scala/sttp/ai/core/agent/AgentBuilder.scala` — `.tools(values: Seq[AgentTool[F, _]])` and `.tools(first, rest*)` both exist. `agent.run(...)` returns `AgentResult[String]` with a `finalAnswer` field.)

- [ ] **Step 3: Add the page to the docs toc**

In `docs/index.md`, the toctree currently lists (around lines 42-44):

```
   agents/quickstart
   agents/configuration
   agents/tools
```

Add a line after `agents/tools`:

```
   agents/mcp
```

- [ ] **Step 4: Verify the documentation compiles**

Run: `sbt compileDocumentation`
Expected: `[success]` — mdoc compiles the new snippet against the local `mcp` module.

- [ ] **Step 5: Full final verification**

```bash
sbt scalafmtAll
sbt scalafmtCheck Test/scalafmtCheck
sbt mcp3/test
sbt compile
```

Expected: all `[success]`; `mcp3/test` all green.

- [ ] **Step 6: Commit**

```bash
git add docs/agents/mcp.md docs/index.md build.sbt
git commit -m "Document MCP tool loading for agents"
```

---

## Deferred (explicitly out of this plan)

- **Runnable scala-cli example in `examples/`** — blocked until the `mcp` artifact is published (CI compiles examples with scala-cli against Maven Central; see Global Constraints). Add after the first release containing this module.
- **Agent-level LLM integration spec** — the spec marks it optional; it would additionally require an external MCP server (e.g. `npx`) inside API-key-gated tests. Revisit if maintainers want it.
- Prompts/resources support, `tools/list_changed` refresh, URI-string constructors — out of scope per the spec.
