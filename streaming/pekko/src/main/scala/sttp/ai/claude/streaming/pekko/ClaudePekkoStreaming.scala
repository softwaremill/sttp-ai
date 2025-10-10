package sttp.ai.claude.streaming.pekko

import org.apache.pekko.stream.scaladsl.{Flow, Source}
import org.apache.pekko.util.ByteString
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.ClaudeExceptions.ClaudeException
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.MessageStreamResponse
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.StreamRequest
import sttp.client4.pekkohttp.PekkoHttpServerSentEvents
import sttp.model.ResponseMetadata
import sttp.model.sse.ServerSentEvent
import sttp.ai.core.json.SnakePickle._

object ClaudePekkoStreaming {
  import MessageStreamResponse.EventData.DoneEvent

  implicit class ClaudeClientPekkoExtension(val client: ClaudeClient) {

    /** Creates and streams a Claude message response as chunk objects for the given message request. The request will complete and the
      * connection close only once the source is fully consumed.
      *
      * @param messageRequest
      *   Message request body.
      */
    def createStreamedMessage(
        messageRequest: MessageRequest
    ): StreamRequest[Either[ClaudeException, Source[MessageStreamResponse, Any]], PekkoStreams] = {
      val request = client
        .createMessageAsBinaryStream(PekkoStreams, messageRequest)

      request.response(request.response.mapWithMetadata(mapEventToResponse))
    }
  }

  private def mapEventToResponse(
      response: Either[ClaudeException, Source[ByteString, Any]],
      metadata: ResponseMetadata
  ): Either[ClaudeException, Source[MessageStreamResponse, Any]] =
    response.map(
      _.via(PekkoHttpServerSentEvents.parse)
        .via(deserializeEvent(metadata))
    )

  private def deserializeEvent(metadata: ResponseMetadata): Flow[ServerSentEvent, MessageStreamResponse, Any] =
    Flow[ServerSentEvent]
      .filter(event => event.data.exists(data => data.trim.nonEmpty && data != DoneEvent))
      .collect { case ServerSentEvent(Some(data), _, _, _) =>
        try
          read[MessageStreamResponse](data)
        catch {
          case e: Exception =>
            throw ClaudeException.DeserializationClaudeException(e, metadata)
        }
      }
}
