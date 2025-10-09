package sttp.openai.json

import sttp.capabilities.Streams
import sttp.client4.ResponseException.UnexpectedStatusCode
import sttp.client4._
import sttp.client4.json._
import sttp.client4.upicklejson.SttpUpickleApi
import sttp.model.ResponseMetadata
import sttp.model.StatusCode._
import sttp.openai.OpenAIExceptions.OpenAIException
import sttp.openai.OpenAIExceptions.OpenAIException._

import java.io.InputStream

/** An sttp upickle api extension that deserializes JSON with snake_case keys into case classes with fields corresponding to keys in
  * camelCase and maps errors to OpenAIException subclasses.
  */
object SttpUpickleApiExtension extends SttpUpickleApi {
  override val upickleApi: SnakePickle.type = SnakePickle

  def asStreamUnsafe_parseErrors[S](s: Streams[S]): StreamResponseAs[Either[OpenAIException, s.BinaryStream], S] =
    asStreamUnsafe(s).mapWithMetadata { (body, meta) =>
      body.left.map(errorBody => httpToOpenAIError(UnexpectedStatusCode(errorBody, meta)))
    }

  def asInputStreamUnsafe_parseErrors: ResponseAs[Either[OpenAIException, InputStream]] =
    asInputStreamUnsafe.mapWithMetadata { (body, meta) =>
      body.left.map(errorBody => httpToOpenAIError(UnexpectedStatusCode(errorBody, meta)))
    }

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

  private def httpToOpenAIError(he: UnexpectedStatusCode[String]): OpenAIException = {
    val errorMessageBody = upickleApi.read[ujson.Value](he.body).apply("error")
    val error = upickleApi.read[Error](errorMessageBody)
    import error._

    he.response.code match {
      case TooManyRequests                              => new RateLimitException(message, `type`, param, code, he)
      case BadRequest | NotFound | UnsupportedMediaType => new InvalidRequestException(message, `type`, param, code, he)
      case Unauthorized                                 => new AuthenticationException(message, `type`, param, code, he)
      case Forbidden                                    => new PermissionException(message, `type`, param, code, he)
      case Conflict                                     => new TryAgain(message, `type`, param, code, he)
      case ServiceUnavailable                           => new ServiceUnavailableException(message, `type`, param, code, he)
      case _                                            => new APIException(message, `type`, param, code, he)
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
