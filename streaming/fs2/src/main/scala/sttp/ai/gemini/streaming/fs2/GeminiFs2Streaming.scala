package sttp.ai.gemini.streaming.fs2

import fs2.{Pipe, RaiseThrowable, Stream}
import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.responses.InteractionStreamEvent
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.StreamRequest
import sttp.client4.impl.fs2.Fs2ServerSentEvents
import sttp.model.ResponseMetadata
import sttp.model.sse.ServerSentEvent
import io.circe.parser.decode
import sttp.ai.gemini.json.GeminiManualCodecs._
import sttp.ai.gemini.json.GeminiDerivedCodecs._

object GeminiFs2Streaming {

  implicit class GeminiClientFs2Extension(val client: GeminiClient) {

    /** Creates and streams an interaction response as SSE event objects for the given request. The request will complete and the connection
      * close only once the source is fully consumed.
      *
      * @param interactionRequest
      *   Interaction request body.
      */
    def createStreamedInteraction[F[_]: RaiseThrowable](
        interactionRequest: InteractionRequest
    ): StreamRequest[Either[GeminiException, Stream[F, InteractionStreamEvent]], Fs2Streams[F]] = {
      val request = client
        .createInteractionAsBinaryStream(Fs2Streams[F], interactionRequest)

      request.response(request.response.mapWithMetadata(mapEventToResponse[F]))
    }
  }

  private def mapEventToResponse[F[_]: RaiseThrowable](
      response: Either[GeminiException, Stream[F, Byte]],
      metadata: ResponseMetadata
  ): Either[GeminiException, Stream[F, InteractionStreamEvent]] =
    response.map(
      _.through(Fs2ServerSentEvents.parse)
        .through(deserializeEvent(metadata))
        .rethrow
    )

  private def deserializeEvent[F[_]](metadata: ResponseMetadata): Pipe[F, ServerSentEvent, Either[Exception, InteractionStreamEvent]] =
    _.filter(_.data.exists(_.trim.nonEmpty))
      .collect { case ServerSentEvent(Some(data), _, _, _) =>
        try
          Right(decode[InteractionStreamEvent](data).fold(throw _, identity))
        catch {
          case e: Exception =>
            Left(GeminiException.DeserializationGeminiException(e, metadata))
        }
      }
}
