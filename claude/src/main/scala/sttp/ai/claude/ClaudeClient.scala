package sttp.ai.claude

import sttp.ai.claude.ClaudeExceptions.{ClaudeException, UnsupportedModelForStructuredOutputException}
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.ClaudeModel
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.{MessageResponse, ModelsResponse}
import sttp.ai.core.http.ResponseHandlers
import sttp.capabilities.Streams
import sttp.client4._
import sttp.model.{ResponseMetadata, Uri}
import io.circe.{Decoder, Json}
import io.circe.parser.decode
import io.circe.syntax._
import sttp.ai.claude.json.ClaudeDerivedCodecs._
import java.io.InputStream

trait ClaudeClient {
  def createMessage(request: MessageRequest): Request[Either[ClaudeException, MessageResponse]]
  def listModels(): Request[Either[ClaudeException, ModelsResponse]]

  def createMessageAsBinaryStream[S](
      streams: Streams[S],
      messageRequest: MessageRequest
  ): StreamRequest[Either[ClaudeException, streams.BinaryStream], S]

  def createMessageAsInputStream(messageRequest: MessageRequest): Request[Either[ClaudeException, java.io.InputStream]]
}

class ClaudeClientImpl(config: ClaudeConfig) extends ClaudeClient with ResponseHandlers[ClaudeException, Decoder] {

  private val claudeUris = new ClaudeUris(config.baseUrl)

  private def claudeAuthRequest =
    basicRequest
      .header("x-api-key", config.apiKey)
      .header("anthropic-version", config.anthropicVersion)
      .header("content-type", "application/json")

  private def claudeAuthRequestForMessage(request: MessageRequest): PartialRequest[Either[String, String]] = {
    if (request.usesStructuredOutput) {
      validateModelForStructuredOutput(request.model)
    }
    claudeAuthRequest
  }

  private def validateModelForStructuredOutput(modelId: String): Unit =
    if (!ClaudeModel.modelSupportsStructuredOutput(modelId)) {
      throw new UnsupportedModelForStructuredOutputException(modelId)
    }

  override def read[T: Decoder](s: String): T = decode[T](s).fold(throw _, identity)

  override def deserializationException(cause: Exception, metadata: ResponseMetadata): ClaudeException =
    ClaudeException.DeserializationClaudeException(cause, metadata)

  override def mapErrorToException(errorResponse: String, metadata: ResponseMetadata): ClaudeException =
    try {
      val errorResp = read[sttp.ai.claude.responses.ErrorResponse](errorResponse)
      mapErrorResponseToException(errorResp, metadata)
    } catch {
      case e: Exception =>
        ClaudeException.DeserializationClaudeException(e, metadata)
    }

  private def mapErrorResponseToException(
      errorResponse: sttp.ai.claude.responses.ErrorResponse,
      metadata: ResponseMetadata
  ): ClaudeException = {
    val error = errorResponse.error
    val cause = ResponseException.UnexpectedStatusCode(error.message, metadata)

    error.`type` match {
      case "authentication_error" =>
        new ClaudeException.AuthenticationException(
          Some(error.message),
          Some(error.`type`),
          None,
          None,
          cause
        )
      case "permission_error" =>
        new ClaudeException.PermissionException(
          Some(error.message),
          Some(error.`type`),
          None,
          None,
          cause
        )
      case "rate_limit_error" =>
        new ClaudeException.RateLimitException(
          Some(error.message),
          Some(error.`type`),
          None,
          None,
          cause
        )
      case "invalid_request_error" =>
        new ClaudeException.InvalidRequestException(
          Some(error.message),
          Some(error.`type`),
          None,
          None,
          cause
        )
      case _ =>
        new ClaudeException.APIException(
          Some(error.message),
          Some(error.`type`),
          None,
          None,
          cause
        )
    }
  }

  override def createMessage(request: MessageRequest): Request[Either[ClaudeException, MessageResponse]] =
    claudeAuthRequestForMessage(request)
      .post(claudeUris.Messages)
      .body(serializeMessageRequest(request))
      .response(asJson_parseErrors[MessageResponse])

  override def listModels(): Request[Either[ClaudeException, ModelsResponse]] =
    claudeAuthRequest
      .get(claudeUris.Models)
      .response(asJson_parseErrors[ModelsResponse])

  override def createMessageAsBinaryStream[S](
      streams: Streams[S],
      messageRequest: MessageRequest
  ): StreamRequest[Either[ClaudeException, streams.BinaryStream], S] = {
    val streamingRequest = messageRequest.copy(stream = Some(true))

    claudeAuthRequestForMessage(streamingRequest)
      .post(claudeUris.Messages)
      .body(serializeMessageRequest(streamingRequest))
      .response(asStreamUnsafe_parseErrors(streams))
  }

  override def createMessageAsInputStream(messageRequest: MessageRequest): Request[Either[ClaudeException, InputStream]] = {
    val streamingRequest = messageRequest.copy(stream = Some(true))

    claudeAuthRequestForMessage(streamingRequest)
      .post(claudeUris.Messages)
      .body(serializeMessageRequest(streamingRequest))
      .response(asInputStreamUnsafe_parseErrors)
  }

  /** Serializes a `MessageRequest` to the JSON body sent on the wire.
    *
    * `deepDropNullValues` is applied to strip unset-`Option` fields (e.g. `temperature: null`), but it also strips null VALUES nested
    * inside array elements, not just absent object fields. That corrupts tool schemas that legitimately contain JSON `null` (e.g.
    * `"enum": ["low", "high", null]`, `"default": null`) as passed through faithfully by [[sttp.ai.claude.models.Tool.CustomRaw]].
    *
    * To keep those schemas byte-faithful while still dropping unset-field nulls everywhere else: capture each tool's `input_schema` from
    * the pre-drop encoding, run `deepDropNullValues` as before, then splice the untouched `input_schema` values back into the cleaned JSON
    * (tools are index-aligned, since `deepDropNullValues` never removes object elements from an array, only null values). Tools without an
    * `input_schema` (i.e. `web_search`) are left alone.
    */
  private def serializeMessageRequest(request: MessageRequest): String =
    dropNullsPreservingInputSchemas(request.asJson).noSpaces

  /** See [[serializeMessageRequest]] for why this exists: applies `deepDropNullValues` to the whole request, then restores each tool's
    * pre-drop `input_schema` verbatim (only `tools[*]` that had an `input_schema` to begin with are touched).
    */
  private def dropNullsPreservingInputSchemas(requestJson: Json): Json = {
    val originalInputSchemas: Option[Vector[Option[Json]]] =
      requestJson.asObject
        .flatMap(_("tools"))
        .flatMap(_.asArray)
        .map(_.map(_.asObject.flatMap(_("input_schema")).filter(!_.isNull)))

    val cleaned = requestJson.deepDropNullValues

    originalInputSchemas match {
      case Some(schemas) =>
        cleaned.mapObject { obj =>
          obj("tools").flatMap(_.asArray) match {
            case Some(cleanedTools) =>
              val restoredTools = cleanedTools.zip(schemas).map {
                case (toolJson, Some(inputSchema)) => toolJson.mapObject(_.add("input_schema", inputSchema))
                case (toolJson, None)              => toolJson
              }
              obj.add("tools", Json.fromValues(restoredTools))
            case None => obj
          }
        }
      case None => cleaned
    }
  }
}

class ClaudeUris(baseUri: Uri) {
  val Messages: Uri = baseUri.addPath("v1", "messages")
  val Models: Uri = baseUri.addPath("v1", "models")
}

object ClaudeClient {

  /** Creates a ClaudeClient using ClaudeConfig.
    *
    * @param config
    *   Claude configuration
    * @return
    *   ClaudeClient instance
    */
  def apply(config: ClaudeConfig): ClaudeClient = new ClaudeClientImpl(config)

  /** Creates a ClaudeClient from environment variables using ClaudeConfig.fromEnv.
    *
    * @return
    *   ClaudeClient instance
    */
  def fromEnv: ClaudeClient = apply(ClaudeConfig.fromEnv)
}
