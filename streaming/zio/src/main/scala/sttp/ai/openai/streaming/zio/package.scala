package sttp.ai.openai.streaming

import _root_.zio.ZIO
import _root_.zio.stream._
import sttp.capabilities.zio.ZioStreams
import sttp.client4.StreamRequest
import sttp.client4.impl.zio.ZioServerSentEvents
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

package object zio {
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
    def createSpeech(requestBody: SpeechRequestBody): StreamRequest[Either[OpenAIException, Stream[Throwable, Byte]], ZioStreams] =
      client.createSpeechAsBinaryStream(ZioStreams, requestBody)

    /** Creates and streams a model response as chunk objects for the given chat conversation defined in chatBody. The request will complete
      * and the connection close only once the source is fully consumed.
      *
      * [[https://platform.openai.com/docs/api-reference/chat/create]]
      *
      * @param chatBody
      *   Chat request body.
      */
    def createStreamedChatCompletion(
        chatBody: ChatBody
    ): StreamRequest[Either[OpenAIException, Stream[Throwable, ChatChunkResponse]], ZioStreams] = {
      val request = client
        .createChatCompletionAsBinaryStream(ZioStreams, chatBody)
      request.response(request.response.mapWithMetadata(mapEventToResponse))
    }

    /** Creates and streams a model response as [[ResponsesStreamEvent]] objects for the request defined in requestBody. The request will
      * complete and the connection close only once the source is fully consumed.
      *
      * [[https://platform.openai.com/docs/api-reference/responses-streaming]]
      *
      * @param requestBody
      *   Model response request body.
      */
    def createStreamedModelResponse(
        requestBody: ResponsesRequestBody
    ): StreamRequest[Either[OpenAIException, Stream[Throwable, ResponsesStreamEvent]], ZioStreams] = {
      val request = client
        .createModelResponseAsBinaryStream(ZioStreams, requestBody)
      request.response(request.response.mapWithMetadata(mapResponsesEventToResponse))
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
    def resumeStreamedModelResponse(
        responseId: String,
        queryParameters: GetResponseQueryParameters = GetResponseQueryParameters.empty
    ): StreamRequest[Either[OpenAIException, Stream[Throwable, ResponsesStreamEvent]], ZioStreams] = {
      val request = client
        .getModelResponseAsBinaryStream(ZioStreams, responseId, queryParameters)
      request.response(request.response.mapWithMetadata(mapResponsesEventToResponse))
    }
  }

  private def mapEventToResponse(
      response: Either[OpenAIException, Stream[Throwable, Byte]],
      metadata: ResponseMetadata
  ): Either[OpenAIException, Stream[Throwable, ChatChunkResponse]] =
    response.map(
      _.viaFunction(ZioServerSentEvents.parse)
        .viaFunction(deserializeEvent(metadata))
    )

  private def deserializeEvent(metadata: ResponseMetadata): ZioStreams.Pipe[ServerSentEvent, ChatChunkResponse] =
    _.takeWhile(_ != DoneEvent)
      .collectZIO { case ServerSentEvent(Some(data), _, _, _) =>
        ZIO.fromEither(deserializeJsonSnake[ChatChunkResponse].apply(data, metadata))
      }

  private def mapResponsesEventToResponse(
      response: Either[OpenAIException, Stream[Throwable, Byte]],
      metadata: ResponseMetadata
  ): Either[OpenAIException, Stream[Throwable, ResponsesStreamEvent]] =
    response.map(
      _.viaFunction(ZioServerSentEvents.parse)
        .viaFunction(deserializeResponsesEvent(metadata))
    )

  private def deserializeResponsesEvent(metadata: ResponseMetadata): ZioStreams.Pipe[ServerSentEvent, ResponsesStreamEvent] =
    _.collectZIO {
      case ServerSentEvent(Some(data), _, _, _) if ResponsesStreamEvent.isEventData(data) =>
        ZIO.fromEither(deserializeJsonSnake[ResponsesStreamEvent].apply(data, metadata))
    }
}
