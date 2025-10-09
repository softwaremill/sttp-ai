package sttp.openai.fixtures

import sttp.client4.ResponseException.UnexpectedStatusCode
import sttp.client4.testing.ResponseStub
import sttp.model.StatusCode
import sttp.model.StatusCode._
import sttp.openai.OpenAIExceptions.OpenAIException
import sttp.openai.OpenAIExceptions.OpenAIException._

object ErrorFixture {
  private val (message, errorType, param, code) = ("Some error message.", "error_type", "null", "invalid_api_key")

  val errorResponse =
    s"""
      |{
      |  "error": {
      |    "message": "$message",
      |    "type": "$errorType",
      |    "param": $param,
      |    "code": "$code"
      |  }
      |}""".stripMargin

  val testData: Seq[(StatusCode, OpenAIException)] = List(
    (
      TooManyRequests,
      new RateLimitException(
        Some(message),
        Some(errorType),
        None,
        Some(code),
        UnexpectedStatusCode(errorResponse, ResponseStub.adjust(errorResponse, TooManyRequests))
      )
    ),
    (
      BadRequest,
      new InvalidRequestException(
        Some(message),
        Some(errorType),
        None,
        Some(code),
        UnexpectedStatusCode(errorResponse, ResponseStub.adjust(errorResponse, BadRequest))
      )
    ),
    (
      NotFound,
      new InvalidRequestException(
        Some(message),
        Some(errorType),
        None,
        Some(code),
        UnexpectedStatusCode(errorResponse, ResponseStub.adjust(errorResponse, NotFound))
      )
    ),
    (
      UnsupportedMediaType,
      new InvalidRequestException(
        Some(message),
        Some(errorType),
        None,
        Some(code),
        UnexpectedStatusCode(errorResponse, ResponseStub.adjust(errorResponse, UnsupportedMediaType))
      )
    ),
    (
      Unauthorized,
      new AuthenticationException(
        Some(message),
        Some(errorType),
        None,
        Some(code),
        UnexpectedStatusCode(errorResponse, ResponseStub.adjust(errorResponse, Unauthorized))
      )
    ),
    (
      Forbidden,
      new PermissionException(
        Some(message),
        Some(errorType),
        None,
        Some(code),
        UnexpectedStatusCode(errorResponse, ResponseStub.adjust(errorResponse, Forbidden))
      )
    ),
    (
      Conflict,
      new TryAgain(
        Some(message),
        Some(errorType),
        None,
        Some(code),
        UnexpectedStatusCode(errorResponse, ResponseStub.adjust(errorResponse, Conflict))
      )
    ),
    (
      ServiceUnavailable,
      new ServiceUnavailableException(
        Some(message),
        Some(errorType),
        None,
        Some(code),
        UnexpectedStatusCode(errorResponse, ResponseStub.adjust(errorResponse, ServiceUnavailable))
      )
    ),
    (
      Gone,
      new APIException(
        Some(message),
        Some(errorType),
        None,
        Some(code),
        UnexpectedStatusCode(errorResponse, ResponseStub.adjust(errorResponse, Gone))
      )
    )
  )
}
