package sttp.ai.openai.streaming.ox

import ox.flow.Flow
import sttp.client4.Request
import sttp.client4.impl.ox.sse.OxServerSentEvents
import sttp.model.ResponseMetadata
import sttp.model.sse.ServerSentEvent
import sttp.ai.openai.OpenAI
import sttp.ai.openai.OpenAIExceptions.OpenAIException
import sttp.ai.openai.json.OpenAIJson.deserializeJsonSnake
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse
import sttp.ai.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse.DoneEvent
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatBody
import sttp.ai.openai.requests.responses.{GetResponseQueryParameters, ResponsesRequestBody, ResponsesStreamEvent}

import java.io.InputStream

extension (client: OpenAI)
  /** Creates and streams a model response as chunk objects for the given chat conversation defined in chatBody.
    *
    * The request will complete and the connection close only once the returned [[Flow]] is fully consumed.
    *
    * [[https://platform.openai.com/docs/api-reference/chat/create]]
    *
    * @param chatBody
    *   Chat request body.
    */
  def createStreamedChatCompletion(
      chatBody: ChatBody
  ): Request[Either[OpenAIException, Flow[Either[Exception, ChatChunkResponse]]]] =
    val request = client
      .createChatCompletionAsInputStream(chatBody)

    request.response(request.response.mapWithMetadata(mapEventToResponse))

  /** Creates and streams a model response as [[ResponsesStreamEvent]] objects for the request defined in requestBody.
    *
    * The request will complete and the connection close only once the returned [[Flow]] is fully consumed.
    *
    * [[https://platform.openai.com/docs/api-reference/responses-streaming]]
    *
    * @param requestBody
    *   Model response request body.
    */
  def createStreamedModelResponse(
      requestBody: ResponsesRequestBody
  ): Request[Either[OpenAIException, Flow[Either[Exception, ResponsesStreamEvent]]]] =
    val request = client
      .createModelResponseAsInputStream(requestBody)

    request.response(request.response.mapWithMetadata(mapResponsesEventToResponse))

  /** Streams the events of an existing model response, optionally resuming after a given sequence number.
    *
    * Only meaningful for a response created with `background = true`: the events are replayed from the stored response, so an interrupted
    * stream can be picked up again by passing the sequence number of the last event received as
    * [[GetResponseQueryParameters.startingAfter]]. The request will complete and the connection close only once the returned [[Flow]] is
    * fully consumed.
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
  ): Request[Either[OpenAIException, Flow[Either[Exception, ResponsesStreamEvent]]]] =
    val request = client
      .getModelResponseAsInputStream(responseId, queryParameters)

    request.response(request.response.mapWithMetadata(mapResponsesEventToResponse))

private def mapEventToResponse(
    response: Either[OpenAIException, InputStream],
    metadata: ResponseMetadata
): Either[OpenAIException, Flow[Either[Exception, ChatChunkResponse]]] =
  response.map(s =>
    OxServerSentEvents
      .parse(s)
      .takeWhile(_ != DoneEvent)
      .collect { case ServerSentEvent(Some(data), _, _, _) =>
        deserializeJsonSnake[ChatChunkResponse].apply(data, metadata)
      }
  )

private def mapResponsesEventToResponse(
    response: Either[OpenAIException, InputStream],
    metadata: ResponseMetadata
): Either[OpenAIException, Flow[Either[Exception, ResponsesStreamEvent]]] =
  response.map(s =>
    OxServerSentEvents
      .parse(s)
      .collect {
        case ServerSentEvent(Some(data), _, _, _) if ResponsesStreamEvent.isEventData(data) =>
          deserializeJsonSnake[ResponsesStreamEvent].apply(data, metadata)
      }
  )
