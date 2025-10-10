package sttp.ai.claude.streaming.zio

import zio.ZIO
import zio.stream._
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.ClaudeExceptions.ClaudeException
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.MessageStreamResponse
import sttp.capabilities.zio.ZioStreams
import sttp.client4.StreamRequest
import sttp.client4.impl.zio.ZioServerSentEvents
import sttp.model.ResponseMetadata
import sttp.model.sse.ServerSentEvent
import sttp.ai.core.json.SnakePickle._

object ClaudeZioStreaming {
  import MessageStreamResponse.EventData.DoneEvent

  implicit class ClaudeClientZioExtension(val client: ClaudeClient) {

    /** Creates and streams a Claude message response as chunk objects for the given message request. The request will complete and the
      * connection close only once the source is fully consumed.
      *
      * @param messageRequest
      *   Message request body.
      */
    def createStreamedMessage(
        messageRequest: MessageRequest
    ): StreamRequest[Either[ClaudeException, Stream[Throwable, MessageStreamResponse]], ZioStreams] = {
      val request = client
        .createMessageAsBinaryStream(ZioStreams, messageRequest)

      request.response(request.response.mapWithMetadata(mapEventToResponse))
    }
  }

  private def mapEventToResponse(
      response: Either[ClaudeException, Stream[Throwable, Byte]],
      metadata: ResponseMetadata
  ): Either[ClaudeException, Stream[Throwable, MessageStreamResponse]] =
    response.map(
      _.viaFunction(ZioServerSentEvents.parse)
        .viaFunction(deserializeEvent(metadata))
    )

  private def deserializeEvent(metadata: ResponseMetadata): ZioStreams.Pipe[ServerSentEvent, MessageStreamResponse] =
    _.filter(event => event.data.exists(data => data.trim.nonEmpty && data != DoneEvent))
      .collectZIO { case ServerSentEvent(Some(data), _, _, _) =>
        ZIO.fromEither(
          try
            Right(read[MessageStreamResponse](data))
          catch {
            case e: Exception =>
              Left(ClaudeException.DeserializationClaudeException(e, metadata))
          }
        )
      }
}
