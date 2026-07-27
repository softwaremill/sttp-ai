package sttp.ai.gemini.streaming.zio

import zio.ZIO
import zio.stream._
import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.GeminiExceptions.GeminiException
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.responses.InteractionStreamEvent
import sttp.capabilities.zio.ZioStreams
import sttp.client4.StreamRequest
import sttp.client4.impl.zio.ZioServerSentEvents
import sttp.model.ResponseMetadata
import sttp.model.sse.ServerSentEvent
import io.circe.parser.decode
import sttp.ai.gemini.json.GeminiManualCodecs._
import sttp.ai.gemini.json.GeminiDerivedCodecs._

object GeminiZioStreaming {

  implicit class GeminiClientZioExtension(val client: GeminiClient) {

    /** Creates and streams an interaction response as SSE event objects for the given request. The request will complete and the connection
      * close only once the source is fully consumed.
      *
      * @param interactionRequest
      *   Interaction request body.
      */
    def createStreamedInteraction(
        interactionRequest: InteractionRequest
    ): StreamRequest[Either[GeminiException, Stream[Throwable, InteractionStreamEvent]], ZioStreams] = {
      val request = client
        .createInteractionAsBinaryStream(ZioStreams, interactionRequest)

      request.response(request.response.mapWithMetadata(mapEventToResponse))
    }
  }

  private def mapEventToResponse(
      response: Either[GeminiException, Stream[Throwable, Byte]],
      metadata: ResponseMetadata
  ): Either[GeminiException, Stream[Throwable, InteractionStreamEvent]] =
    response.map(
      _.viaFunction(ZioServerSentEvents.parse)
        .viaFunction(deserializeEvent(metadata))
    )

  private def deserializeEvent(metadata: ResponseMetadata): ZioStreams.Pipe[ServerSentEvent, InteractionStreamEvent] =
    _.filter(_.data.exists(_.trim.nonEmpty))
      .collectZIO { case ServerSentEvent(Some(data), _, _, _) =>
        ZIO.fromEither(
          try
            Right(decode[InteractionStreamEvent](data).fold(throw _, identity))
          catch {
            case e: Exception =>
              Left(GeminiException.DeserializationGeminiException(e, metadata))
          }
        )
      }
}
