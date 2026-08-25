package sttp.ai.openai.streaming

import _root_.akka.stream.scaladsl.{Flow, Source}
import _root_.akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.client4.StreamRequest
import sttp.client4.akkahttp.AkkaHttpServerSentEvents
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

package object akka {
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
    def createSpeech(requestBody: SpeechRequestBody): StreamRequest[Either[OpenAIException, Source[ByteString, Any]], AkkaStreams] =
      client.createSpeechAsBinaryStream(AkkaStreams, requestBody)

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
    ): StreamRequest[Either[OpenAIException, Source[ChatChunkResponse, Any]], AkkaStreams] = {
      val request = client
        .createChatCompletionAsBinaryStream(AkkaStreams, chatBody)

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
    ): StreamRequest[Either[OpenAIException, Source[ResponsesStreamEvent, Any]], AkkaStreams] = {
      val request = client
        .createModelResponseAsBinaryStream(AkkaStreams, requestBody)

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
    ): StreamRequest[Either[OpenAIException, Source[ResponsesStreamEvent, Any]], AkkaStreams] = {
      val request = client
        .getModelResponseAsBinaryStream(AkkaStreams, responseId, queryParameters)

      request.response(request.response.mapWithMetadata(mapResponsesEventToResponse))
    }
  }

  private def mapEventToResponse(
      response: Either[OpenAIException, Source[ByteString, Any]],
      metadata: ResponseMetadata
  ): Either[OpenAIException, Source[ChatChunkResponse, Any]] =
    response.map(
      _.via(AkkaHttpServerSentEvents.parse)
        .via(deserializeEvent(metadata))
    )

  private def deserializeEvent(metadata: ResponseMetadata): Flow[ServerSentEvent, ChatChunkResponse, Any] =
    Flow[ServerSentEvent]
      .takeWhile(_ != DoneEvent)
      .collect { case ServerSentEvent(Some(data), _, _, _) =>
        deserializeJsonSnake[ChatChunkResponse].apply(data, metadata) match {
          case Left(exception) => throw exception
          case Right(value)    => value
        }
      }

  private def mapResponsesEventToResponse(
      response: Either[OpenAIException, Source[ByteString, Any]],
      metadata: ResponseMetadata
  ): Either[OpenAIException, Source[ResponsesStreamEvent, Any]] =
    response.map(
      _.via(AkkaHttpServerSentEvents.parse)
        .via(deserializeResponsesEvent(metadata))
    )

  private def deserializeResponsesEvent(metadata: ResponseMetadata): Flow[ServerSentEvent, ResponsesStreamEvent, Any] =
    Flow[ServerSentEvent]
      .collect {
        case ServerSentEvent(Some(data), _, _, _) if ResponsesStreamEvent.isEventData(data) =>
          deserializeJsonSnake[ResponsesStreamEvent].apply(data, metadata) match {
            case Left(exception) => throw exception
            case Right(value)    => value
          }
      }
}
