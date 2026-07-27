package sttp.ai.gemini

import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.responses.InteractionResponse
import sttp.ai.core.http.ResponseHandlers
import sttp.capabilities.Streams
import sttp.client4._
import sttp.model.{ResponseMetadata, StatusCode, Uri}
import io.circe.{Decoder, Json}
import io.circe.parser.decode
import io.circe.syntax._
import sttp.ai.gemini.json.GeminiDerivedCodecs._
import java.io.InputStream

trait GeminiClient {
  def createInteraction(request: InteractionRequest): Request[Either[GeminiException, InteractionResponse]]
  def getInteraction(id: String): Request[Either[GeminiException, InteractionResponse]]
  def deleteInteraction(id: String): Request[Either[GeminiException, Unit]]
  def cancelInteraction(id: String): Request[Either[GeminiException, InteractionResponse]]

  def createInteractionAsBinaryStream[S](
      streams: Streams[S],
      request: InteractionRequest
  ): StreamRequest[Either[GeminiException, streams.BinaryStream], S]

  def createInteractionAsInputStream(request: InteractionRequest): Request[Either[GeminiException, InputStream]]
}

class GeminiClientImpl(config: GeminiConfig) extends GeminiClient with ResponseHandlers[GeminiException, Decoder] {

  private val geminiUris = new GeminiUris(config.baseUrl)

  private def geminiAuthRequest =
    basicRequest
      .header("x-goog-api-key", config.apiKey)
      .header("content-type", "application/json")

  override def read[T: Decoder](s: String): T = decode[T](s).fold(throw _, identity)

  override def deserializationException(cause: Exception, metadata: ResponseMetadata): GeminiException =
    GeminiException.DeserializationGeminiException(cause, metadata)

  /** Maps an error response to an exception based on the HTTP status code. The Gemini error body carries a numeric `code` and a Google
    * status string (e.g. `RESOURCE_EXHAUSTED`) rather than a stable error `type`, so the HTTP status is the reliable dispatch key.
    */
  override def mapErrorToException(errorResponse: String, metadata: ResponseMetadata): GeminiException = {
    val (message, status) =
      decode[sttp.ai.gemini.responses.ErrorResponse](errorResponse) match {
        case Right(parsed) => (Some(parsed.error.message), parsed.error.status)
        case Left(_)       => (Some(errorResponse), None)
      }
    val cause = ResponseException.UnexpectedStatusCode(message.getOrElse(""), metadata)

    metadata.code match {
      case StatusCode.Unauthorized       => new GeminiException.AuthenticationException(message, status, None, None, cause)
      case StatusCode.Forbidden          => new GeminiException.PermissionException(message, status, None, None, cause)
      case StatusCode.TooManyRequests    => new GeminiException.RateLimitException(message, status, None, None, cause)
      case StatusCode.BadRequest         => new GeminiException.InvalidRequestException(message, status, None, None, cause)
      case StatusCode.NotFound           => new GeminiException.NotFoundException(message, status, None, None, cause)
      case StatusCode.ServiceUnavailable => new GeminiException.ServiceUnavailableException(message, status, None, None, cause)
      case _                             => new GeminiException.APIException(message, status, None, None, cause)
    }
  }

  /** Parse a successful JSON response into `T`, mapping non-2xx responses to the corresponding [[GeminiException]] (rate limit, invalid
    * request, etc.) rather than a generic deserialization error. The core default (`ResponseHandlers.asJson_parseErrors`) treats
    * `asString`'s `Left(body)` for non-2xx responses as a deserialization failure, so it never reaches `mapErrorToException`; this override
    * restores status-based dispatch, matching `OpenAIJson.asJson_parseErrors`.
    */
  override def asJson_parseErrors[T: Decoder]: ResponseAs[Either[GeminiException, T]] =
    asString.mapWithMetadata { (responseBody, metadata) =>
      responseBody match {
        case Left(errorBody) => Left(mapErrorToException(errorBody, metadata))
        case Right(body)     =>
          try Right(read[T](body))
          catch {
            case e: Exception => Left(deserializationException(e, metadata))
          }
      }
    }

  private def asUnit_parseErrors: ResponseAs[Either[GeminiException, Unit]] =
    asString.mapWithMetadata { (body, metadata) =>
      body match {
        case Right(_)    => Right(())
        case Left(error) => Left(mapErrorToException(error, metadata))
      }
    }

  override def createInteraction(request: InteractionRequest): Request[Either[GeminiException, InteractionResponse]] =
    geminiAuthRequest
      .post(geminiUris.Interactions)
      .body(serializeInteractionRequest(request))
      .response(asJson_parseErrors[InteractionResponse])

  override def getInteraction(id: String): Request[Either[GeminiException, InteractionResponse]] =
    geminiAuthRequest
      .get(geminiUris.interaction(id))
      .response(asJson_parseErrors[InteractionResponse])

  override def deleteInteraction(id: String): Request[Either[GeminiException, Unit]] =
    geminiAuthRequest
      .delete(geminiUris.interaction(id))
      .response(asUnit_parseErrors)

  override def cancelInteraction(id: String): Request[Either[GeminiException, InteractionResponse]] =
    geminiAuthRequest
      .post(geminiUris.cancel(id))
      .response(asJson_parseErrors[InteractionResponse])

  override def createInteractionAsBinaryStream[S](
      streams: Streams[S],
      request: InteractionRequest
  ): StreamRequest[Either[GeminiException, streams.BinaryStream], S] = {
    val streamingRequest = request.copy(stream = Some(true))

    geminiAuthRequest
      .post(geminiUris.Interactions)
      .body(serializeInteractionRequest(streamingRequest))
      .response(asStreamUnsafe_parseErrors(streams))
  }

  override def createInteractionAsInputStream(request: InteractionRequest): Request[Either[GeminiException, InputStream]] = {
    val streamingRequest = request.copy(stream = Some(true))

    geminiAuthRequest
      .post(geminiUris.Interactions)
      .body(serializeInteractionRequest(streamingRequest))
      .response(asInputStreamUnsafe_parseErrors)
  }

  /** Serializes an [[InteractionRequest]] to the JSON body sent on the wire.
    *
    * `deepDropNullValues` strips unset-`Option` fields, but it would also strip legitimate JSON `null`s nested inside tool parameter
    * schemas (e.g. `"enum": ["low", "high", null]`). To keep those schemas byte-faithful: capture each tool's `parameters` from the
    * pre-drop encoding, run `deepDropNullValues`, then splice the untouched `parameters` values back (tools are index-aligned, since
    * `deepDropNullValues` never removes array elements, only null values). Built-in tools without `parameters` are left alone. Same
    * approach as `ClaudeClientImpl.dropNullsPreservingInputSchemas`.
    */
  private def serializeInteractionRequest(request: InteractionRequest): String =
    dropNullsPreservingToolParameters(request.asJson).noSpaces

  private def dropNullsPreservingToolParameters(requestJson: Json): Json = {
    val originalParameters: Option[Vector[Option[Json]]] =
      requestJson.asObject
        .flatMap(_("tools"))
        .flatMap(_.asArray)
        .map(_.map(_.asObject.flatMap(_("parameters")).filter(!_.isNull)))

    val cleaned = requestJson.deepDropNullValues

    originalParameters match {
      case Some(parameters) =>
        cleaned.mapObject { obj =>
          obj("tools").flatMap(_.asArray) match {
            case Some(cleanedTools) =>
              val restoredTools = cleanedTools.zip(parameters).map {
                case (toolJson, Some(params)) => toolJson.mapObject(_.add("parameters", params))
                case (toolJson, None)         => toolJson
              }
              obj.add("tools", Json.fromValues(restoredTools))
            case None => obj
          }
        }
      case None => cleaned
    }
  }
}

class GeminiUris(baseUri: Uri) {
  val Interactions: Uri = baseUri.addPath("v1beta", "interactions")
  def interaction(id: String): Uri = baseUri.addPath("v1beta", "interactions", id)
  def cancel(id: String): Uri = baseUri.addPath("v1beta", "interactions", id, "cancel")
}

object GeminiClient {

  /** Creates a GeminiClient using GeminiConfig. */
  def apply(config: GeminiConfig): GeminiClient = new GeminiClientImpl(config)

  /** Creates a GeminiClient from environment variables using GeminiConfig.fromEnv. */
  def fromEnv: GeminiClient = apply(GeminiConfig.fromEnv)
}
