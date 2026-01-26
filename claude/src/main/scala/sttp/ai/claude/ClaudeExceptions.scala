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

  class UnsupportedModelForStructuredOutputException(val modelId: String)
      extends AIException(
        message = Some(
          s"Model '$modelId' does not support structured output. " +
            "Structured output is only supported by Claude 4.x models (e.g., claude-sonnet-4-1-20250514, claude-opus-4-1-20250514)."
        ),
        `type` = Some("unsupported_model_for_structured_output"),
        param = Some("model"),
        code = Some("model_not_supported")
      )

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
