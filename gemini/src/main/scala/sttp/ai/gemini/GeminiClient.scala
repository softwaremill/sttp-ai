package sttp.ai.gemini

import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.responses.InteractionResponse
import sttp.ai.core.http.ResponseHandlers
import sttp.capabilities.Streams
import sttp.client4._
import sttp.model.{ResponseMetadata, StatusCode, Uri}
import io.circe.{Decoder, Json, JsonObject}
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

private[gemini] class GeminiClientImpl(config: GeminiConfig) extends GeminiClient with ResponseHandlers[GeminiException, Decoder] {

  private val geminiUris = new GeminiUris(config.baseUrl)

  private def geminiAuthRequest =
    config.authHeaders.foldLeft(basicRequest) { case (request, (name, value)) => request.header(name, value) }

  override def read[T: Decoder](s: String): T = decode[T](s).fold(throw _, identity)

  override def deserializationException(cause: Exception, metadata: ResponseMetadata): GeminiException =
    GeminiException.DeserializationGeminiException(cause, metadata)

  /** Maps an error response to an exception based on the HTTP status code. The Gemini error body carries a numeric `code` and a Google
    * status string (e.g. `RESOURCE_EXHAUSTED`) rather than a stable error `type`, so the HTTP status is the reliable dispatch key.
    */
  override def mapErrorToException(errorResponse: String, metadata: ResponseMetadata): GeminiException = {
    val (message, status, code) =
      decode[sttp.ai.gemini.responses.ErrorResponse](errorResponse) match {
        case Right(parsed) => (Some(parsed.error.message), parsed.error.status, parsed.error.code)
        case Left(_)       => (Some(errorResponse), None, None)
      }
    val cause = ResponseException.UnexpectedStatusCode(message.getOrElse(""), metadata)

    metadata.code match {
      case StatusCode.Unauthorized       => new GeminiException.AuthenticationException(message, status, None, code, cause)
      case StatusCode.Forbidden          => new GeminiException.PermissionException(message, status, None, code, cause)
      case StatusCode.TooManyRequests    => new GeminiException.RateLimitException(message, status, None, code, cause)
      case StatusCode.BadRequest         => new GeminiException.InvalidRequestException(message, status, None, code, cause)
      case StatusCode.NotFound           => new GeminiException.NotFoundException(message, status, None, code, cause)
      case StatusCode.ServiceUnavailable => new GeminiException.ServiceUnavailableException(message, status, None, code, cause)
      case _                             => new GeminiException.APIException(message, status, None, code, cause)
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
    * Our derived encoders emit `null` for every unset `Option` field (e.g. `"temperature": null`); those must be dropped. But several
    * request fields carry caller-supplied JSON that must reach the API byte-faithful, including any legitimate `null`s inside it (e.g.
    * `"enum": ["low", "high", null]`, `"default": null`): the `response_format` schema, each tool's `parameters` schema, and replayed
    * conversation data in `input[*].arguments` / `input[*].result`. So instead of `deepDropNullValues` (which would also strip nulls inside
    * those subtrees — and removes null array elements to boot), this walks the document dropping null object fields everywhere except
    * inside the protected subtrees, and never touches array elements.
    */
  private def serializeInteractionRequest(request: InteractionRequest): String =
    dropNullsOutsideVerbatimJson(request.asJson, path = Nil).noSpaces

  /** Object-field paths whose subtrees are caller-supplied verbatim JSON. `*` matches any array index. */
  private val VerbatimJsonPaths: Set[List[String]] = Set(
    List("response_format"),
    List("tools", "*", "parameters"),
    List("input", "*", "arguments"),
    List("input", "*", "result")
  )

  private def dropNullsOutsideVerbatimJson(json: Json, path: List[String]): Json =
    if (VerbatimJsonPaths.contains(path)) json
    else
      json.arrayOrObject(
        json,
        arr => Json.fromValues(arr.map(dropNullsOutsideVerbatimJson(_, path :+ "*"))),
        obj =>
          Json.fromJsonObject(
            JsonObject.fromIterable(
              obj.toIterable.collect { case (key, value) if !value.isNull => key -> dropNullsOutsideVerbatimJson(value, path :+ key) }
            )
          )
      )
}

private[gemini] class GeminiUris(baseUri: Uri) {
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
