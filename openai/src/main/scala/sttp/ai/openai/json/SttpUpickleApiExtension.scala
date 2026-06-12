package sttp.ai.openai.json

import sttp.client4.ResponseException.UnexpectedStatusCode
import sttp.client4._
import sttp.client4.json._
import sttp.client4.upicklejson.SttpUpickleApi
import sttp.model.ResponseMetadata
import sttp.model.StatusCode._
import sttp.ai.openai.OpenAIExceptions.OpenAIException
import sttp.ai.openai.OpenAIExceptions.OpenAIException._
import sttp.ai.core.http.ResponseHandlers

/** An sttp upickle api extension that deserializes JSON with snake_case keys into case classes with fields corresponding to keys in
  * camelCase and maps errors to OpenAIException subclasses.
  */
object SttpUpickleApiExtension extends SttpUpickleApi with ResponseHandlers[OpenAIException, sttp.ai.core.json.SnakePickle.Reader] {
  override val upickleApi: sttp.ai.core.json.SnakePickle.type = sttp.ai.core.json.SnakePickle

  override def read[T: sttp.ai.core.json.SnakePickle.Reader](s: String): T = upickleApi.read[T](s)

  override def deserializationException(cause: Exception, metadata: ResponseMetadata): OpenAIException =
    DeserializationOpenAIException(cause, metadata)

  override def mapErrorToException(errorResponse: String, metadata: ResponseMetadata): OpenAIException =
    httpToOpenAIError(UnexpectedStatusCode(errorResponse, metadata))

  def asJson_parseErrors[B: upickleApi.Reader: IsOption]: ResponseAs[Either[OpenAIException, B]] =
    asString.mapWithMetadata(deserializeRightWithMappedExceptions(deserializeJsonSnake)).showAsJson

  private def deserializeRightWithMappedExceptions[T](
      doDeserialize: (String, ResponseMetadata) => Either[DeserializationOpenAIException, T]
  ): (Either[String, String], ResponseMetadata) => Either[OpenAIException, T] = {
    case (Left(body), meta) =>
      Left(httpToOpenAIError(UnexpectedStatusCode(body, meta)))
    case (Right(body), meta) => doDeserialize.apply(body, meta)
  }

  def deserializeJsonSnake[B: upickleApi.Reader: IsOption]: (String, ResponseMetadata) => Either[DeserializationOpenAIException, B] = {
    (s: String, meta: ResponseMetadata) =>
      try
        Right(upickleApi.read[B](JsonInput.sanitize[B].apply(s)))
      catch {
        case e: Exception => Left(DeserializationOpenAIException(e, meta))
        case t: Throwable =>
          // in ScalaJS, ArrayIndexOutOfBoundsException exceptions are wrapped in org.scalajs.linker.runtime.UndefinedBehaviorError
          t.getCause match {
            case e: ArrayIndexOutOfBoundsException => Left(DeserializationOpenAIException(e, meta))
            case _                                 => throw t
          }
      }
  }

  def asStringEither: ResponseAs[Either[OpenAIException, String]] =
    asStringAlways
      .mapWithMetadata { (string, metadata) =>
        if (metadata.isSuccess) Right(string) else Left(httpToOpenAIError(UnexpectedStatusCode(string, metadata)))
      }
      .showAs("either(as error, as string)")

  /** Max number of characters of a raw error body kept as the fallback message when it can't be parsed as a standard OpenAI error. */
  private val MaxRawErrorBodyLength = 500

  private def httpToOpenAIError(he: UnexpectedStatusCode[String]): OpenAIException = {
    // Fallback for bodies that aren't the standard {"error": {...}} OpenAI shape (e.g. Azure's {"statusCode":...,"message":...},
    // a non-object JSON root, or non-JSON): expose a truncated raw body and the status code rather than throwing.
    val fallback = (Some(he.body.take(MaxRawErrorBodyLength)), None, None, Some(he.response.code.toString))

    val (message, tpe, param, code) =
      try
        upickleApi
          .read[ujson.Value](he.body)
          .objOpt
          .flatMap(_.get("error")) match {
          case Some(errorNode) =>
            val error = upickleApi.read[Error](errorNode)
            (error.message, error.`type`, error.param, error.code)
          case None => fallback
        }
      catch {
        case _: Exception => fallback
      }

    he.response.code match {
      case TooManyRequests                              => new RateLimitException(message, tpe, param, code, he)
      case BadRequest | NotFound | UnsupportedMediaType => new InvalidRequestException(message, tpe, param, code, he)
      case Unauthorized                                 => new AuthenticationException(message, tpe, param, code, he)
      case Forbidden                                    => new PermissionException(message, tpe, param, code, he)
      case Conflict                                     => new TryAgain(message, tpe, param, code, he)
      case ServiceUnavailable                           => new ServiceUnavailableException(message, tpe, param, code, he)
      case _                                            => new APIException(message, tpe, param, code, he)
    }
  }

  private case class Error(
      message: Option[String] = None,
      `type`: Option[String] = None,
      param: Option[String] = None,
      code: Option[String] = None
  )
  private object Error {
    implicit val errorR: upickleApi.Reader[Error] = upickleApi.macroR
  }
}
