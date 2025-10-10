package sttp.ai.core.http

import sttp.ai.core.error.AIException
import sttp.client4._
import sttp.model.ResponseMetadata
import sttp.capabilities.Streams
import java.io.InputStream

/** Trait providing common response handling for AI APIs
  *
  * This trait abstracts the common patterns for handling HTTP responses, JSON deserialization, and error mapping across different AI APIs.
  */
trait ResponseHandlers[E <: AIException, Reader[_]] {

  /** JSON reader for type T */
  def read[T: Reader](s: String): T

  /** Create a deserialization exception specific to the API */
  def deserializationException(cause: Exception, metadata: ResponseMetadata): E

  /** Map error response to API-specific exception */
  def mapErrorToException(errorResponse: String, metadata: ResponseMetadata): E

  /** Parse JSON response with error mapping */
  def asJson_parseErrors[T: Reader]: ResponseAs[Either[E, T]] =
    asString.mapWithMetadata { (responseBody, metadata) =>
      responseBody match {
        case Left(error) =>
          Left(deserializationException(new Exception(error), metadata))
        case Right(body) =>
          try
            Right(read[T](body))
          catch {
            case e: Exception =>
              try
                Left(mapErrorToException(body, metadata))
              catch {
                case _: Exception =>
                  Left(deserializationException(e, metadata))
              }
          }
      }
    }

  /** Handle binary stream responses with error mapping */
  def asStreamUnsafe_parseErrors[S](s: Streams[S]): StreamResponseAs[Either[E, s.BinaryStream], S] =
    asStreamUnsafe(s).mapWithMetadata { (streamResponse, metadata) =>
      if (metadata.isSuccess) {
        streamResponse match {
          case Right(stream) => Right(stream)
          case Left(error) =>
            Left(deserializationException(new Exception(error), metadata))
        }
      } else {
        Left(mapErrorToException(metadata.statusText, metadata))
      }
    }

  /** Handle input stream responses with error mapping */
  def asInputStreamUnsafe_parseErrors: ResponseAs[Either[E, InputStream]] =
    asInputStreamUnsafe.mapWithMetadata { (body, meta) =>
      if (meta.isSuccess) {
        body match {
          case Right(stream) => Right(stream)
          case Left(error) =>
            Left(deserializationException(new Exception(error), meta))
        }
      } else {
        Left(mapErrorToException(meta.statusText, meta))
      }
    }
}
