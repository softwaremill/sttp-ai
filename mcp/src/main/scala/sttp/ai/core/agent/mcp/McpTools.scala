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
class McpToolConversionException(message: String, cause: Throwable) extends RuntimeException(message, cause) {
  def this(message: String) = this(message, null)
}

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
    *   calls or to manually defined tools; combining those is the caller's responsibility (`namePrefix` is the recommended way to keep them
    *   distinct).
    */
  def fromClient[F[_]](
      client: McpClient[F],
      namePrefix: Option[String] = None
  )(using monad: MonadError[F]): F[Seq[AgentTool[F, Map[String, Json]]]] =
    listAllTools(client, cursor = None, acc = Vector.empty).flatMap(definitions => monad.eval(buildTools(client, definitions, namePrefix)))

  /** Builds the agent tools, failing before any schema is decoded when the listing cannot be exposed safely:
    *   - a tool whose name sanitizes to nothing (e.g. a name made entirely of characters outside `[A-Za-z0-9_-]`, or an empty name) could
    *     never be called by name
    *   - tools that end up with the same exposed name (a prefix/sanitization collision, or a server-side name reused for genuinely
    *     different tools) would route calls to the wrong tool
    *
    * An MCP server re-listing the exact same tool across pages is harmless and is silently deduplicated rather than treated as a collision
    * — only names that collide while their underlying definitions differ are a real ambiguity.
    *
    * Building tools (and so throwing, if needed) happens inside `monad.eval` rather than a plain `.map`: for effect types that distinguish
    * recoverable failures from defects (e.g. ZIO), throwing directly inside `.map` would surface as an unrecoverable defect instead of a
    * typed failure the caller's `ExceptionHandler`/error-handling can see.
    */
  private def buildTools[F[_]](client: McpClient[F], definitions: Vector[ToolDefinition], namePrefix: Option[String])(using
      MonadError[F]
  ): Seq[AgentTool[F, Map[String, Json]]] = {
    val distinctDefinitions = definitions.distinct
    val withExposedNames = distinctDefinitions.map(d => exposedNameFor(d.name, namePrefix) -> d.name)

    val emptyNames = withExposedNames.collect { case ("", original) => original }
    if (emptyNames.nonEmpty)
      throw new McpToolConversionException(
        s"MCP tool(s) sanitize to an empty exposed name: ${emptyNames.mkString("'", "', '", "'")}. Rename them on the server."
      )

    val collisions = withExposedNames.groupBy(_._1).filter(_._2.sizeIs > 1)
    if (collisions.nonEmpty) {
      val described = collisions.toSeq
        .sortBy(_._1)
        .map { case (exposed, colliding) => s"'$exposed' (original names: ${colliding.map(_._2).mkString("'", "', '", "'")})" }
        .mkString("; ")
      throw new McpToolConversionException(
        s"Multiple MCP tools map to the same exposed name: $described. " +
          "Rename the tools on the server or load the servers with distinct namePrefix values."
      )
    }

    distinctDefinitions.map(d => toAgentTool(client, d, namePrefix))
  }

  /** Backends constrain the tool names they accept (OpenAI function calling enforces `^[a-zA-Z0-9_-]{1,64}$`), while MCP permits dots,
    * slashes, non-ASCII characters and longer names. The exposed (LLM-facing) name is sanitized to the cross-backend-safe form — characters
    * outside `[A-Za-z0-9_-]` become `_`, truncated to 64 characters — after any `namePrefix` is applied; the original name is still used
    * when calling the server. Names made up entirely of non-ASCII/unsupported characters collapse to a run of underscores; if two such
    * names collapse to the same result, `fromClient`'s duplicate check (in `buildTools`) rejects the listing rather than silently merging
    * them.
    */
  private def sanitizeName(name: String): String = {
    def legal(c: Char): Boolean =
      (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-'
    name.iterator.map(c => if (legal(c)) c else '_').mkString.take(64)
  }

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

  private def toAgentTool[F[_]](client: McpClient[F], definition: ToolDefinition, namePrefix: Option[String])(using
      MonadError[F]
  ): AgentTool[F, Map[String, Json]] = {
    val schema = definition.inputSchema.as[Schema] match {
      case Right(s)    => s
      case Left(error) =>
        throw new McpToolConversionException(s"Cannot decode the input schema of MCP tool '${definition.name}': ${error.getMessage}", error)
    }
    val exposedName = exposedNameFor(definition.name, namePrefix)
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
