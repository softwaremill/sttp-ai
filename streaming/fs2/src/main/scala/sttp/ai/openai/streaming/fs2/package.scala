package sttp.ai.openai.streaming

import _root_.fs2.{Pipe, RaiseThrowable, Stream}
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.StreamRequest
import sttp.client4.impl.fs2.Fs2ServerSentEvents
import sttp.model.ResponseMetadata
import sttp.model.sse.ServerSentEvent
import sttp.ai.openai.OpenAI
import sttp.ai.openai.OpenAIExceptions.OpenAIException
import sttp.ai.openai.json.OpenAIJson.deserializeJsonSnake
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.requests.audio.speech.SpeechRequestBody
import sttp.ai.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatBody
import sttp.ai.openai.requests.responses.{GetResponseQueryParameters, ResponsesRequestBody, ResponsesStreamEvent}

package object fs2 {
  import ChatChunkResponse.DoneEvent

  implicit class extension(val client: OpenAI) {

    /** Generates audio from the input text.
      *
      * [[https://platform.openai.com/docs/api-reference/audio/createSpeech]]
      *
      * @param requestBody
      *   Request body that will be used to create a speech.
      *
      * @return
      *   The audio file content.
      */
    def createSpeech[F[_]: RaiseThrowable](
        requestBody: SpeechRequestBody
    ): StreamRequest[Either[OpenAIException, Stream[F, Byte]], Fs2Streams[F]] =
      client.createSpeechAsBinaryStream(Fs2Streams[F], requestBody)

    /** Creates and streams a model response as chunk objects for the given chat conversation defined in chatBody. The request will complete
      * and the connection close only once the source is fully consumed.
      *
      * [[https://platform.openai.com/docs/api-reference/chat/create]]
      *
      * @param chatBody
      *   Chat request body.
      */
    def createStreamedChatCompletion[F[_]: RaiseThrowable](
        chatBody: ChatBody
    ): StreamRequest[Either[OpenAIException, Stream[F, ChatChunkResponse]], Fs2Streams[F]] = {
      val request = client
        .createChatCompletionAsBinaryStream(Fs2Streams[F], chatBody)

      request.response(request.response.mapWithMetadata(mapEventToResponse[F]))
    }

    /** Creates and streams a model response as [[ResponsesStreamEvent]] objects for the request defined in requestBody. The request will
      * complete and the connection close only once the source is fully consumed.
      *
      * [[https://platform.openai.com/docs/api-reference/responses-streaming]]
      *
      * @param requestBody
      *   Model response request body.
      */
    def createStreamedModelResponse[F[_]: RaiseThrowable](
        requestBody: ResponsesRequestBody
    ): StreamRequest[Either[OpenAIException, Stream[F, ResponsesStreamEvent]], Fs2Streams[F]] = {
      val request = client
        .createModelResponseAsBinaryStream(Fs2Streams[F], requestBody)

      request.response(request.response.mapWithMetadata(mapResponsesEventToResponse[F]))
    }

    /** Streams the events of an existing model response, optionally resuming after a given sequence number.
      *
      * Only meaningful for a response created with `background = true`: the events are replayed from the stored response, so an interrupted
      * stream can be picked up again by passing the sequence number of the last event received as
      * [[GetResponseQueryParameters.startingAfter]]. The request will complete and the connection close only once the source is fully
      * consumed.
      *
      * [[https://platform.openai.com/docs/api-reference/responses/get]]
      *
      * @param responseId
      *   The ID of the response to stream.
      * @param queryParameters
      *   Query parameters; `stream` is always sent as `true`.
      */
    def resumeStreamedModelResponse[F[_]: RaiseThrowable](
        responseId: String,
        queryParameters: GetResponseQueryParameters = GetResponseQueryParameters.empty
    ): StreamRequest[Either[OpenAIException, Stream[F, ResponsesStreamEvent]], Fs2Streams[F]] = {
      val request = client
        .getModelResponseAsBinaryStream(Fs2Streams[F], responseId, queryParameters)

      request.response(request.response.mapWithMetadata(mapResponsesEventToResponse[F]))
    }
  }

  private def mapEventToResponse[F[_]: RaiseThrowable](
      response: Either[OpenAIException, Stream[F, Byte]],
      metadata: ResponseMetadata
  ): Either[OpenAIException, Stream[F, ChatChunkResponse]] =
    response.map(
      _.through(Fs2ServerSentEvents.parse)
        .through(deserializeEvent(metadata))
        .rethrow
    )

  private def deserializeEvent[F[_]](metadata: ResponseMetadata): Pipe[F, ServerSentEvent, Either[Exception, ChatChunkResponse]] =
    _.takeWhile(_ != DoneEvent)
      .collect { case ServerSentEvent(Some(data), _, _, _) =>
        deserializeJsonSnake[ChatChunkResponse].apply(data, metadata)
      }

  private def mapResponsesEventToResponse[F[_]: RaiseThrowable](
      response: Either[OpenAIException, Stream[F, Byte]],
      metadata: ResponseMetadata
  ): Either[OpenAIException, Stream[F, ResponsesStreamEvent]] =
    response.map(
      _.through(Fs2ServerSentEvents.parse)
        .through(deserializeResponsesEvent(metadata))
        .rethrow
    )

  private def deserializeResponsesEvent[F[_]](
      metadata: ResponseMetadata
  ): Pipe[F, ServerSentEvent, Either[Exception, ResponsesStreamEvent]] =
    _.collect {
      case ServerSentEvent(Some(data), _, _, _) if ResponsesStreamEvent.isEventData(data) =>
        deserializeJsonSnake[ResponsesStreamEvent].apply(data, metadata)
    }
}
