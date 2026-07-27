package sttp.ai.gemini

import sttp.ai.gemini.GeminiExceptions.GeminiException.DeserializationGeminiException
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.models.ResponseFormat
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.responses.InteractionResponse
import io.circe.Decoder
import io.circe.parser.decode
import io.circe.syntax._
import sttp.apispec.circe._
import sttp.client4.{DefaultSyncBackend, SyncBackend}
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TapirSchema}

class GeminiSyncClient(config: GeminiConfig, backend: SyncBackend = DefaultSyncBackend()) {
  private val client = new GeminiClientImpl(config)

  def createInteraction(request: InteractionRequest): InteractionResponse =
    client.createInteraction(request).send(backend).body match {
      case Left(exception) => throw exception
      case Right(response) => response
    }

  /** Creates an interaction with a JSON-schema response format derived from `T`'s tapir schema (unless the request already carries one) and
    * decodes the model output text as `T`.
    */
  def createInteractionAs[T: TapirSchema: Decoder](request: InteractionRequest): T = {
    val withSchema =
      if (request.usesStructuredOutput) request
      else {
        val schemaJson = TapirSchemaToJsonSchema(implicitly[TapirSchema[T]], markOptionsAsNullable = true).asJson.deepDropNullValues
        request.withStructuredOutput(ResponseFormat.JsonSchema(name = "response", schema = schemaJson))
      }

    val response = createInteraction(withSchema)

    decode[T](response.outputText) match {
      case Right(value) => value
      case Left(e)      =>
        throw new DeserializationGeminiException(s"Failed to parse structured output: ${e.getMessage}", null)
    }
  }

  def getInteraction(id: String): InteractionResponse =
    client.getInteraction(id).send(backend).body match {
      case Left(exception) => throw exception
      case Right(response) => response
    }

  def deleteInteraction(id: String): Unit =
    client.deleteInteraction(id).send(backend).body match {
      case Left(exception) => throw exception
      case Right(_)        => ()
    }

  def cancelInteraction(id: String): InteractionResponse =
    client.cancelInteraction(id).send(backend).body match {
      case Left(exception) => throw exception
      case Right(response) => response
    }

  def close(): Unit = backend.close()
}

object GeminiSyncClient {

  def apply(config: GeminiConfig): GeminiSyncClient = new GeminiSyncClient(config)

  def apply(config: GeminiConfig, backend: SyncBackend): GeminiSyncClient = new GeminiSyncClient(config, backend)

  def fromEnv: GeminiSyncClient = apply(GeminiConfig.fromEnv)

  def fromEnv(backend: SyncBackend): GeminiSyncClient = apply(GeminiConfig.fromEnv, backend)
}
