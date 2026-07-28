package sttp.ai.gemini.streaming.ox

import ox.flow.Flow
import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.responses.InteractionStreamEvent
import sttp.ai.gemini.responses.InteractionStreamEvent.DoneEvent
import io.circe.parser.decode
import sttp.ai.gemini.json.GeminiDerivedCodecs._
import sttp.client4.Request
import sttp.client4.impl.ox.sse.OxServerSentEvents
import sttp.model.ResponseMetadata
import sttp.model.sse.ServerSentEvent

import java.io.InputStream

extension (client: GeminiClient)
  /** Creates and streams an interaction response as SSE event objects for the given request.
    *
    * The request will complete and the connection close only once the returned [[Flow]] is fully consumed.
    *
    * @param interactionRequest
    *   Interaction request body.
    */
  def createStreamedInteraction(
      interactionRequest: InteractionRequest
  ): Request[Either[GeminiException, Flow[Either[Exception, InteractionStreamEvent]]]] =
    val request = client
      .createInteractionAsInputStream(interactionRequest)

    request.response(request.response.mapWithMetadata(mapEventToResponse))

private def mapEventToResponse(
    response: Either[GeminiException, InputStream],
    metadata: ResponseMetadata
): Either[GeminiException, Flow[Either[Exception, InteractionStreamEvent]]] =
  response.map(s =>
    OxServerSentEvents
      .parse(s)
      .filter(_.data.exists(data => data.trim.nonEmpty && data != DoneEvent))
      .collect { case ServerSentEvent(Some(data), _, _, _) =>
        try
          Right(decode[InteractionStreamEvent](data).fold(throw _, identity))
        catch {
          case e: Exception =>
            Left(GeminiException.DeserializationGeminiException(e, metadata))
        }
      }
  )
