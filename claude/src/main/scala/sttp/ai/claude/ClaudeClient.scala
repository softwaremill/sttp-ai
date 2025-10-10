package sttp.ai.claude

import sttp.ai.claude.ClaudeExceptions.ClaudeException
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.{MessageResponse, ModelsResponse}
import sttp.ai.core.http.ResponseHandlers
import sttp.capabilities.Streams
import sttp.client4._
import sttp.model.{ResponseMetadata, Uri}
import sttp.ai.core.json.SnakePickle._
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

class ClaudeClientImpl(config: ClaudeConfig) extends ClaudeClient with ResponseHandlers[ClaudeException, Reader] {

  private val claudeUris = new ClaudeUris(config.baseUrl)

  private def claudeAuthRequest =
    basicRequest
      .header("x-api-key", config.apiKey)
      .header("anthropic-version", config.anthropicVersion)
      .header("content-type", "application/json")

  // Implementation of ResponseHandlers methods
  override def read[T: Reader](s: String): T = sttp.ai.core.json.SnakePickle.read[T](s)

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
    claudeAuthRequest
      .post(claudeUris.Messages)
      .body(write(request))
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

    claudeAuthRequest
      .post(claudeUris.Messages)
      .body(write(streamingRequest))
      .response(asStreamUnsafe_parseErrors(streams))
  }

  override def createMessageAsInputStream(messageRequest: MessageRequest): Request[Either[ClaudeException, InputStream]] = {
    val streamingRequest = messageRequest.copy(stream = Some(true))

    claudeAuthRequest
      .post(claudeUris.Messages)
      .body(write(streamingRequest))
      .response(asInputStreamUnsafe_parseErrors)
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
  def apply(config: ClaudeConfig): ClaudeClientImpl = new ClaudeClientImpl(config)

  /** Creates a ClaudeClient from environment variables using ClaudeConfig.fromEnv.
    *
    * @return
    *   ClaudeClient instance
    */
  def fromEnv: ClaudeClientImpl = apply(ClaudeConfig.fromEnv)
}
