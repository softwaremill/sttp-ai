package sttp.ai.claude

import sttp.ai.core.error.AIException
import sttp.client4.ResponseException
import sttp.client4.ResponseException.{DeserializationException, UnexpectedStatusCode}
import sttp.model.ResponseMetadata

object ClaudeExceptions {
  sealed abstract class ClaudeException(
      message: Option[String],
      `type`: Option[String],
      param: Option[String],
      code: Option[String],
      cause: ResponseException[String]
  ) extends AIException(message, `type`, param, code, cause)

  object ClaudeException {
    class DeserializationClaudeException(
        message: String,
        cause: DeserializationException
    ) extends ClaudeException(Some(message), None, None, None, cause)

    object DeserializationClaudeException {
      def apply(cause: DeserializationException): DeserializationClaudeException =
        new DeserializationClaudeException(cause.getMessage, cause)

      def apply(cause: Exception, meta: ResponseMetadata): DeserializationClaudeException = apply(
        DeserializationException(cause.getMessage, cause, meta)
      )
    }

    class RateLimitException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends ClaudeException(message, `type`, param, code, cause)

    class InvalidRequestException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends ClaudeException(message, `type`, param, code, cause)

    class AuthenticationException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends ClaudeException(message, `type`, param, code, cause)

    class PermissionException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends ClaudeException(message, `type`, param, code, cause)

    class TryAgain(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends ClaudeException(message, `type`, param, code, cause)

    class ServiceUnavailableException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends ClaudeException(message, `type`, param, code, cause)

    class APIException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends ClaudeException(message, `type`, param, code, cause)
  }
}
