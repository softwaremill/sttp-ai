package sttp.ai.claude

import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.{MessageResponse, ModelsResponse}
import sttp.client4.{DefaultSyncBackend, SyncBackend}

class ClaudeSyncClient(config: ClaudeConfig, backend: SyncBackend = DefaultSyncBackend()) {
  private val client = new ClaudeClientImpl(config)

  def createMessage(request: MessageRequest): MessageResponse =
    client.createMessage(request).send(backend).body match {
      case Left(exception) => throw exception
      case Right(response) => response
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
