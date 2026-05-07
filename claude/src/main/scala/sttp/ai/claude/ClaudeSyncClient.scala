package sttp.ai.claude

import sttp.ai.claude.ClaudeExceptions.ClaudeException.DeserializationClaudeException
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, OutputFormat}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.{MessageResponse, ModelsResponse}
import sttp.ai.core.json.SnakePickle
import sttp.client4.{DefaultSyncBackend, SyncBackend}
import sttp.tapir.{Schema => TapirSchema}

class ClaudeSyncClient(config: ClaudeConfig, backend: SyncBackend = DefaultSyncBackend()) {
  private val client = new ClaudeClientImpl(config)

  def createMessage(request: MessageRequest): MessageResponse =
    client.createMessage(request).send(backend).body match {
      case Left(exception) => throw exception
      case Right(response) => response
    }

  def createMessageAs[T: TapirSchema: SnakePickle.Reader](request: MessageRequest): T = {
    val withSchema =
      if (request.usesStructuredOutput) request
      else request.withStructuredOutput(OutputFormat.JsonSchema.withTapirSchema[T])

    val response = createMessage(withSchema)

    val text = response.content.collect { case ContentBlock.TextContent(t, _) => t }.mkString

    try SnakePickle.read[T](text)
    catch {
      case e: Exception =>
        throw new DeserializationClaudeException(s"Failed to parse structured output: ${e.getMessage}", null)
    }
  }

  def listModels(): ModelsResponse =
    client.listModels().send(backend).body match {
      case Left(exception) => throw exception
      case Right(response) => response
    }

  def close(): Unit = backend.close()
}

object ClaudeSyncClient {

  def apply(config: ClaudeConfig): ClaudeSyncClient = new ClaudeSyncClient(config)

  def apply(config: ClaudeConfig, backend: SyncBackend): ClaudeSyncClient = new ClaudeSyncClient(config, backend)

  def fromEnv: ClaudeSyncClient = apply(ClaudeConfig.fromEnv)

  def fromEnv(backend: SyncBackend): ClaudeSyncClient = apply(ClaudeConfig.fromEnv, backend)
}
