package sttp.ai.gemini

import sttp.ai.core.error.AIException
import sttp.client4.ResponseException
import sttp.client4.ResponseException.{DeserializationException, UnexpectedStatusCode}
import sttp.model.ResponseMetadata

object GeminiExceptions {
  sealed abstract class GeminiException(
      message: Option[String],
      `type`: Option[String],
      param: Option[String],
      code: Option[String],
      cause: ResponseException[String]
  ) extends AIException(message, `type`, param, code, cause)

  object GeminiException {
    class DeserializationGeminiException(
        message: String,
        cause: DeserializationException
    ) extends GeminiException(Some(message), None, None, None, cause)

    object DeserializationGeminiException {
      def apply(cause: DeserializationException): DeserializationGeminiException =
        new DeserializationGeminiException(cause.getMessage, cause)

      def apply(cause: Exception, meta: ResponseMetadata): DeserializationGeminiException = apply(
        DeserializationException(cause.getMessage, cause, meta)
      )
    }

    class RateLimitException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends GeminiException(message, `type`, param, code, cause)

    class InvalidRequestException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends GeminiException(message, `type`, param, code, cause)

    class AuthenticationException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends GeminiException(message, `type`, param, code, cause)

    class PermissionException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends GeminiException(message, `type`, param, code, cause)

    class NotFoundException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends GeminiException(message, `type`, param, code, cause)

    class ServiceUnavailableException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends GeminiException(message, `type`, param, code, cause)

    class APIException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends GeminiException(message, `type`, param, code, cause)
  }
}
