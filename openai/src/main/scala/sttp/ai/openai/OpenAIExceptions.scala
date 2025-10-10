package sttp.ai.openai

import sttp.ai.core.error.AIException
import sttp.client4.ResponseException
import sttp.client4.ResponseException.{DeserializationException, UnexpectedStatusCode}
import sttp.model.ResponseMetadata

object OpenAIExceptions {
  sealed abstract class OpenAIException(
      message: Option[String],
      `type`: Option[String],
      param: Option[String],
      code: Option[String],
      cause: ResponseException[String]
  ) extends AIException(message, `type`, param, code, cause)

  object OpenAIException {
    class DeserializationOpenAIException(
        message: String,
        cause: DeserializationException
    ) extends OpenAIException(Some(message), None, None, None, cause)

    object DeserializationOpenAIException {
      def apply(cause: DeserializationException): DeserializationOpenAIException =
        new DeserializationOpenAIException(cause.getMessage, cause)

      def apply(cause: Exception, meta: ResponseMetadata): DeserializationOpenAIException = apply(
        DeserializationException(cause.getMessage, cause, meta)
      )
    }
    class RateLimitException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends OpenAIException(message, `type`, param, code, cause)

    class InvalidRequestException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends OpenAIException(message, `type`, param, code, cause)

    class AuthenticationException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends OpenAIException(message, `type`, param, code, cause)

    class PermissionException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends OpenAIException(message, `type`, param, code, cause)

    class TryAgain(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends OpenAIException(message, `type`, param, code, cause)

    class ServiceUnavailableException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends OpenAIException(message, `type`, param, code, cause)

    class APIException(
        message: Option[String],
        `type`: Option[String],
        param: Option[String],
        code: Option[String],
        cause: UnexpectedStatusCode[String]
    ) extends OpenAIException(message, `type`, param, code, cause)
  }
}
