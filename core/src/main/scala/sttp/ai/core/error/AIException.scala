package sttp.ai.core.error

import sttp.client4.ResponseException

/** Base trait for AI API exceptions
  *
  * This provides a common exception hierarchy for both OpenAI and Claude APIs.
  */
abstract class AIException(
    val message: Option[String],
    val `type`: Option[String],
    val param: Option[String],
    val code: Option[String],
    val cause: ResponseException[String]
) extends Exception(cause.getMessage, cause)
