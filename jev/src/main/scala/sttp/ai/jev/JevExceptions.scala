package sttp.ai.jev

import sttp.ai.core.error.AIException
import sttp.client4.ResponseException
import sttp.client4.ResponseException.{DeserializationException, UnexpectedStatusCode}
import sttp.model.ResponseMetadata

object JevExceptions:
  /** Base of every failure the Jev clients report. `requestId` is the server's `x-typesafe-request-id`, when the response carried one.
    * `type` is the server's `error_type` when it sent one.
    */
  sealed abstract class JevException(
      message: Option[String],
      `type`: Option[String],
      cause: ResponseException[String],
      val requestId: Option[String]
  ) extends AIException(message, `type`, None, None, cause)

  object JevException:
    /** 400 or 422: the request was rejected. Server messages name questions by their 0-based position in the request, e.g.
      * `questions.2.score.criteria`.
      */
    class InvalidRequestException(
        message: Option[String],
        `type`: Option[String],
        cause: UnexpectedStatusCode[String],
        requestId: Option[String]
    ) extends JevException(message, `type`, cause, requestId)

    /** 401 or 403: the API key is invalid or missing. */
    class AuthenticationException(
        message: Option[String],
        `type`: Option[String],
        cause: UnexpectedStatusCode[String],
        requestId: Option[String]
    ) extends JevException(message, `type`, cause, requestId)

    /** 429: rate limit exceeded; retry after backoff. */
    class RateLimitException(
        message: Option[String],
        `type`: Option[String],
        cause: UnexpectedStatusCode[String],
        requestId: Option[String]
    ) extends JevException(message, `type`, cause, requestId)

    /** 529: the service is overloaded; retry with backoff. */
    class OverloadedException(
        message: Option[String],
        `type`: Option[String],
        cause: UnexpectedStatusCode[String],
        requestId: Option[String]
    ) extends JevException(message, `type`, cause, requestId)

    /** Any other non-2xx status. */
    class APIException(
        message: Option[String],
        `type`: Option[String],
        cause: UnexpectedStatusCode[String],
        requestId: Option[String]
    ) extends JevException(message, `type`, cause, requestId)

    /** A 2xx body that could not be decoded, including an answer outside the question's option or level set. */
    class DeserializationJevException(
        message: String,
        cause: DeserializationException,
        requestId: Option[String]
    ) extends JevException(Some(message), None, cause, requestId)

    object DeserializationJevException:
      private[jev] def apply(
          body: String,
          cause: Exception,
          metadata: ResponseMetadata,
          requestId: Option[String]
      ): DeserializationJevException =
        new DeserializationJevException(cause.getMessage, DeserializationException(body, cause, metadata), requestId)
