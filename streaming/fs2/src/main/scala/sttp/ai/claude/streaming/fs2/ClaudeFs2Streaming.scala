package sttp.ai.claude.streaming.fs2

import fs2.{Pipe, RaiseThrowable, Stream}
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.ClaudeExceptions.ClaudeException
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.MessageStreamResponse
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.StreamRequest
import sttp.client4.impl.fs2.Fs2ServerSentEvents
import sttp.model.ResponseMetadata
import sttp.model.sse.ServerSentEvent
import sttp.ai.core.json.SnakePickle._

object ClaudeFs2Streaming {
  import MessageStreamResponse.EventData.DoneEvent

  implicit class ClaudeClientFs2Extension(val client: ClaudeClient) {

    /** Creates and streams a Claude message response as chunk objects for the given message request. The request will complete and the
      * connection close only once the source is fully consumed.
      *
      * @param messageRequest
      *   Message request body.
      */
    def createStreamedMessage[F[_]: RaiseThrowable](
        messageRequest: MessageRequest
    ): StreamRequest[Either[ClaudeException, Stream[F, MessageStreamResponse]], Fs2Streams[F]] = {
      val request = client
        .createMessageAsBinaryStream(Fs2Streams[F], messageRequest)

      request.response(request.response.mapWithMetadata(mapEventToResponse[F]))
    }
  }

  private def mapEventToResponse[F[_]: RaiseThrowable](
      response: Either[ClaudeException, Stream[F, Byte]],
      metadata: ResponseMetadata
  ): Either[ClaudeException, Stream[F, MessageStreamResponse]] =
    response.map(
      _.through(Fs2ServerSentEvents.parse)
        .through(deserializeEvent(metadata))
        .rethrow
    )

  private def deserializeEvent[F[_]](metadata: ResponseMetadata): Pipe[F, ServerSentEvent, Either[Exception, MessageStreamResponse]] =
    _.filter(_.data.exists(data => data.trim.nonEmpty && data != DoneEvent))
      .collect { case ServerSentEvent(Some(data), _, _, _) =>
        try
          Right(read[MessageStreamResponse](data))
        catch {
          case e: Exception =>
            Left(ClaudeException.DeserializationClaudeException(e, metadata))
        }
      }
}
