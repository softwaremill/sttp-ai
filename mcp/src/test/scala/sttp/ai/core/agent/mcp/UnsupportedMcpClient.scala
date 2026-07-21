package sttp.ai.core.agent.mcp

import chimp.client.McpClient
import chimp.protocol.*

/** Base for MCP client test doubles that only need to implement a couple of [[chimp.client.McpClient]]'s members. `serverCapabilities`/
  * `serverInfo` return a generic placeholder, overridable if a test ever needs otherwise. `listTools` is left abstract: every consumer
  * calls it at least once. `ping`/`close` are also left abstract, NOT included below: they are harmless lifecycle no-ops a test could
  * legitimately call and expect to succeed, unlike the members below, which no test double built on this trait is expected to ever invoke
  * and so throw if they are.
  */
trait UnsupportedMcpClient[F[_]] extends McpClient[F] {
  override def serverCapabilities: ServerCapabilities = ServerCapabilities()
  override def serverInfo: Implementation = Implementation("fake-server", "0.0.1")

  override def callTool(name: String, arguments: io.circe.Json): F[CallToolResult] = unsupported
  override def listPrompts(cursor: Option[Cursor]): F[ListPromptsResult] = unsupported
  override def getPrompt(name: String, arguments: Map[String, String]): F[GetPromptResult] = unsupported
  override def listResources(cursor: Option[Cursor]): F[ListResourcesResult] = unsupported
  override def listResourceTemplates(cursor: Option[Cursor]): F[ListResourceTemplatesResult] = unsupported
  override def readResource(uri: String): F[ReadResourceResult] = unsupported
  override def complete(ref: CompleteRef, argument: CompleteArgument): F[CompleteResult] = unsupported
  override def setLoggingLevel(level: LoggingLevel): F[Unit] = unsupported
  override def sendProgress(token: ProgressToken, progress: Double, total: Option[Double], message: Option[String]): F[Unit] = unsupported

  protected def unsupported: Nothing = throw new UnsupportedOperationException("not used by this test double")
}
