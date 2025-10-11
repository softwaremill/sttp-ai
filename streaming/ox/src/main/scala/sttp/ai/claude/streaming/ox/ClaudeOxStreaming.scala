package sttp.ai.claude.streaming.ox

import ox.flow.Flow
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.ClaudeExceptions.ClaudeException
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.MessageStreamResponse
import sttp.ai.claude.responses.MessageStreamResponse.EventData.DoneEvent
import sttp.ai.core.json.SnakePickle.*
import sttp.client4.Request
import sttp.client4.impl.ox.sse.OxServerSentEvents
import sttp.model.ResponseMetadata
import sttp.model.sse.ServerSentEvent

import java.io.InputStream

extension (client: ClaudeClient)
  /** Creates and streams a Claude message response as chunk objects for the given message request.
    *
    * The request will complete and the connection close only once the returned [[Flow]] is fully consumed.
    *
    * @param messageRequest
    *   Message request body.
    */
  def createStreamedMessage(
      messageRequest: MessageRequest
  ): Request[Either[ClaudeException, Flow[Either[Exception, MessageStreamResponse]]]] =
    val request = client
      .createMessageAsInputStream(messageRequest)

    request.response(request.response.mapWithMetadata(mapEventToResponse))

private def mapEventToResponse(
    response: Either[ClaudeException, InputStream],
    metadata: ResponseMetadata
): Either[ClaudeException, Flow[Either[Exception, MessageStreamResponse]]] =
  response.map(s =>
    OxServerSentEvents
      .parse(s)
      .filter(event => event.data.exists(data => data.trim.nonEmpty && data != DoneEvent))
      .collect { case ServerSentEvent(Some(data), _, _, _) =>
        try
          Right(read[MessageStreamResponse](data))
        catch {
          case e: Exception =>
            Left(ClaudeException.DeserializationClaudeException(e, metadata))
        }
      }
  )
