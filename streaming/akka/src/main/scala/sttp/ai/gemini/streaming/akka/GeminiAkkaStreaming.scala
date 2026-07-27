package sttp.ai.gemini.streaming.akka

import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.responses.InteractionStreamEvent
import sttp.capabilities.akka.AkkaStreams
import sttp.client4.StreamRequest
import sttp.client4.akkahttp.AkkaHttpServerSentEvents
import sttp.model.ResponseMetadata
import sttp.model.sse.ServerSentEvent
import io.circe.parser.decode
import sttp.ai.gemini.json.GeminiManualCodecs._
import sttp.ai.gemini.json.GeminiDerivedCodecs._

object GeminiAkkaStreaming {

  implicit class GeminiClientAkkaExtension(val client: GeminiClient) {

    /** Creates and streams an interaction response as SSE event objects for the given request. The request will complete and the connection
      * close only once the source is fully consumed.
      *
      * @param interactionRequest
      *   Interaction request body.
      */
    def createStreamedInteraction(
        interactionRequest: InteractionRequest
    ): StreamRequest[Either[GeminiException, Source[InteractionStreamEvent, Any]], AkkaStreams] = {
      val request = client
        .createInteractionAsBinaryStream(AkkaStreams, interactionRequest)

      request.response(request.response.mapWithMetadata(mapEventToResponse))
    }
  }

  private def mapEventToResponse(
      response: Either[GeminiException, Source[ByteString, Any]],
      metadata: ResponseMetadata
  ): Either[GeminiException, Source[InteractionStreamEvent, Any]] =
    response.map(
      _.via(AkkaHttpServerSentEvents.parse)
        .via(deserializeEvent(metadata))
    )

  private def deserializeEvent(metadata: ResponseMetadata): Flow[ServerSentEvent, InteractionStreamEvent, Any] =
    Flow[ServerSentEvent]
      .filter(_.data.exists(_.trim.nonEmpty))
      .collect { case ServerSentEvent(Some(data), _, _, _) =>
        try
          decode[InteractionStreamEvent](data).fold(throw _, identity)
        catch {
          case e: Exception =>
            throw GeminiException.DeserializationGeminiException(e, metadata)
        }
      }
}
