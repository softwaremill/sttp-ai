package sttp.ai.gemini

import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.GeminiExceptions.GeminiException.DeserializationGeminiException
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.models.ResponseFormat
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.responses.InteractionResponse
import sttp.ai.core.http.SyncRetries
import io.circe.Decoder
import io.circe.parser.decode
import sttp.client4.{DefaultSyncBackend, Request, SyncBackend}
import sttp.tapir.{Schema => TapirSchema}

class GeminiSyncClient(config: GeminiConfig, backend: SyncBackend = DefaultSyncBackend()) {
  private val client = new GeminiClientImpl(config)

  private def sendOrThrow[A](request: Request[Either[GeminiException, A]]): A =
    SyncRetries.sendWithRetries(backend, request, config.maxRetries).body match {
      case Left(exception) => throw exception
      case Right(value)    => value
    }

  def createInteraction(request: InteractionRequest): InteractionResponse =
    sendOrThrow(client.createInteraction(request))

  /** Creates an interaction with a JSON-schema response format derived from `T`'s tapir schema (unless the request already carries one) and
    * decodes the model output text as `T`.
    */
  def createInteractionAs[T: TapirSchema: Decoder](request: InteractionRequest): T = {
    val withSchema =
      if (request.usesStructuredOutput) request
      else request.withStructuredOutput(ResponseFormat.JsonSchema.withTapirSchema[T])

    val response = createInteraction(withSchema)

    decode[T](response.outputText) match {
      case Right(value) => value
      case Left(e)      =>
        throw new DeserializationGeminiException(s"Failed to parse structured output: ${e.getMessage}", null)
    }
  }

  def getInteraction(id: String): InteractionResponse =
    sendOrThrow(client.getInteraction(id))

  def deleteInteraction(id: String): Unit =
    sendOrThrow(client.deleteInteraction(id))

  def cancelInteraction(id: String): InteractionResponse =
    sendOrThrow(client.cancelInteraction(id))

  def close(): Unit = backend.close()
}

object GeminiSyncClient {

  def apply(config: GeminiConfig): GeminiSyncClient = new GeminiSyncClient(config)

  def apply(config: GeminiConfig, backend: SyncBackend): GeminiSyncClient = new GeminiSyncClient(config, backend)

  def fromEnv: GeminiSyncClient = apply(GeminiConfig.fromEnv)

  def fromEnv(backend: SyncBackend): GeminiSyncClient = apply(GeminiConfig.fromEnv, backend)
}
