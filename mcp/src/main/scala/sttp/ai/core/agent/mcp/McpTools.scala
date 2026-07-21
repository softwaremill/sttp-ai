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
    * The server's original input schema `Json` is preserved verbatim and exposed via `rawJsonSchema`, so backends that consume it can pass
    * it through to the model without the lossy round-trip of re-encoding the decoded [[sttp.apispec.Schema]] (which stringifies `null` enum
    * members and drops null array elements/defaults).
    *
    * Tool calls are executed remotely via `tools/call`. Before calling, arguments whose value is JSON `null` are stripped unless the
    * corresponding parameter is listed in the tool's original top-level `required` array: this accommodates backends (e.g. OpenAI strict
    * mode) that normalize optional parameters into explicit `null`s, which schema-validating MCP servers may otherwise reject. Nulls for
    * genuinely required-but-nullable parameters are preserved. Results are rendered as text: text content blocks are joined with newlines,
    * other content blocks are rendered as compact JSON, and an empty content list falls back to the tool's structured content. Results
    * marked as errors by the server are prefixed with `"Tool execution failed: "` and returned to the LLM as regular tool output.
    * Transport-level failures surface as exceptions and are subject to the agent's configured `ExceptionHandler`.
    *
    * @param namePrefix
    *   When defined, tools are exposed to the LLM as a sanitized form of `s"${prefix}_${name}"` (see `sanitizeName`), to avoid collisions
    *   with manually defined tools or tools loaded from other MCP servers. The original name is still used when calling the server.
    *   `fromClient` fails with [[McpToolConversionException]] if, after sanitizing, two tools from THIS server end up with the same exposed
    *   name (e.g. one server tool named `my.tool` and another named `my/tool`) — this check does not extend across multiple `fromClient`
    *   calls or to manually defined tools: an agent's tool lookup is by name, so if you combine tools from several sources without keeping
    *   their exposed names distinct, the last one loaded silently shadows any earlier tool with the same name — no error is raised, calls
    *   intended for one tool can silently execute another. `namePrefix` is the recommended way to keep multiple sources distinct.
    */
  def fromClient[F[_]](
      client: McpClient[F],
      namePrefix: Option[String] = None
  )(using monad: MonadError[F]): F[Seq[AgentTool[F, Map[String, Json]]]] =
    listAllTools(client, cursor = None, acc = Vector.empty).flatMap { definitions =>
      // `buildTools` can throw (see its doc); a plain `.map`/`monad.eval` would only route that safely through F's error channel if
      // this particular MonadError's `map`/`eval` happens to catch exceptions, which not all of them do (e.g. the built-in `EitherMonad`
      // does not). Catching explicitly and going through `monad.unit`/`monad.error` is guaranteed safe for every MonadError instance.
      scala.util.Try(buildTools(client, definitions, namePrefix)) match {
        case scala.util.Success(tools) => monad.unit(tools)
        case scala.util.Failure(e)     => monad.error(e)
      }
    }

  /** Builds the agent tools, failing before any schema is decoded when the listing cannot be exposed safely:
    *   - a tool with an empty original name can never be called correctly (`tools/call` would be sent an empty name), so this is rejected
    *     directly on the original name, before any `namePrefix`/sanitization is applied — sanitizing first would miss it, since
    *     `exposedNameFor` always inserts a literal `"_"` between a prefix and the name, so the exposed name itself can never come out empty
    *     once a `namePrefix` is set
    *   - tools that end up with the same exposed name (a prefix/sanitization collision, or a server-side name reused for genuinely
    *     different tools) would route calls to the wrong tool
    *
    * An MCP server re-listing the same tool across pages is harmless and is silently deduplicated rather than treated as a collision, even
    * if its free-form `_meta` differs between listings (`_meta` is reserved by MCP for implementation-specific metadata, not structural
    * tool identity) — only names that collide while the rest of their definition differs are a real ambiguity.
    */
  private def buildTools[F[_]](client: McpClient[F], definitions: Vector[ToolDefinition], namePrefix: Option[String])(using
      MonadError[F]
  ): Seq[AgentTool[F, Map[String, Json]]] = {
    val distinctDefinitions = definitions.distinctBy(_.copy(_meta = None))

    // Checked on the ORIGINAL name, before any prefix is applied: `exposedNameFor` always inserts a literal "_" between a namePrefix and
    // the name, so the exposed name can never actually come out empty once a namePrefix is set (e.g. an empty name with namePrefix =
    // Some("p") sanitizes to "p_", not ""), even though the server would still be called with the empty original name.
    val emptyOriginalNames = distinctDefinitions.filter(_.name.isEmpty)
    if (emptyOriginalNames.nonEmpty)
      throw new McpToolConversionException("This MCP server advertised a tool with an empty name. Rename it on the server.", null)

    val withExposedNames = distinctDefinitions.map(d => exposedNameFor(d.name, namePrefix) -> d)

    val collisions = withExposedNames.groupBy(_._1).filter(_._2.sizeIs > 1)
    if (collisions.nonEmpty) {
      // namePrefix is NOT a usable remedy here: it is applied identically to every tool from this one fromClient call, and
      // exposedNameFor's sanitization is a pure per-character transform, so two names that already collide keep colliding under any
      // shared prefix (sanitize(p + "_" + a) == sanitize(p + "_" + b) whenever sanitize(a) == sanitize(b)). namePrefix only helps
      // distinguish tools loaded from DIFFERENT fromClient calls (see the docs) -- for a collision reported here, the only fix is
      // renaming one of the tools on the server.
      val described = collisions.toSeq
        .sortBy(_._1)
        .map { case (exposed, colliding) => s"'$exposed' (original names: ${colliding.map(_._2.name).mkString("'", "', '", "'")})" }
        .mkString("; ")
      throw new McpToolConversionException(
        s"Multiple MCP tools map to the same exposed name: $described. Rename the tools on the server.",
        null
      )
    }

    withExposedNames.map { case (exposedName, d) => toAgentTool(client, d, exposedName) }
  }

  /** Backends constrain the tool names they accept (OpenAI function calling enforces `^[a-zA-Z0-9_-]{1,64}$`), while MCP permits dots,
    * slashes, non-ASCII characters and longer names. The exposed (LLM-facing) name is sanitized to the cross-backend-safe form — characters
    * outside `[A-Za-z0-9_-]` become `_`, truncated to 64 characters — after any `namePrefix` is applied; the original name is still used
    * when calling the server. Names made up entirely of non-ASCII/unsupported characters collapse to a run of underscores; if two such
    * names collapse to the same result, `fromClient`'s duplicate check (in `buildTools`) rejects the listing rather than silently merging
    * them.
    */
  private def sanitizeName(name: String): String =
    name.replaceAll("[^A-Za-z0-9_-]", "_").take(64)

  private def exposedNameFor(originalName: String, namePrefix: Option[String]): String =
    sanitizeName(namePrefix.fold(originalName)(prefix => s"${prefix}_$originalName"))

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

  private def toAgentTool[F[_]](client: McpClient[F], definition: ToolDefinition, exposedName: String)(using
      MonadError[F]
  ): AgentTool[F, Map[String, Json]] = {
    val schema = definition.inputSchema.as[Schema] match {
      case Right(s)    => s
      case Left(error) =>
        throw new McpToolConversionException(s"Cannot decode the input schema of MCP tool '${definition.name}': ${error.getMessage}", error)
    }
    val description = definition.description.orElse(definition.title).getOrElse(definition.name)
    val requiredParams =
      definition.inputSchema.hcursor.downField("required").as[List[String]].toOption.getOrElse(Nil).toSet
    val delegate = AgentTool.dynamicF(exposedName, description, schema) { input =>
      val filtered = input.filterNot { case (k, v) => v.isNull && !requiredParams(k) }
      client.callTool(definition.name, Json.fromFields(filtered)).map(renderResult)
    }
    new AgentTool[F, Map[String, Json]] {
      override def name: String = delegate.name
      override def description: String = delegate.description
      override def jsonSchema: Schema = delegate.jsonSchema
      override def codec: io.circe.Codec[Map[String, Json]] = delegate.codec
      override def execute(input: Map[String, Json]): F[String] = delegate.execute(input)
      override def rawJsonSchema: Json = definition.inputSchema
    }
  }

  private[mcp] def renderResult(result: CallToolResult): String = {
    val blocks = result.content.map {
      case text: ToolContent.Text => text.text
      case other                  => other.asJson.noSpaces
    }
    val body =
      if (blocks.nonEmpty) blocks.mkString("\n")
      else result.structuredContent.map(_.noSpaces).getOrElse("")
    if (result.isError) {
      val details = if (body.isEmpty) "(no details provided by the MCP server)" else body
      s"Tool execution failed: $details"
    } else body
  }
}
