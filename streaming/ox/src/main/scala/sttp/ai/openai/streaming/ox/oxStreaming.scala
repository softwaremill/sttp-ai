package sttp.openai.streaming.ox

import ox.flow.Flow
import sttp.client4.Request
import sttp.client4.impl.ox.sse.OxServerSentEvents
import sttp.model.ResponseMetadata
import sttp.model.sse.ServerSentEvent
import sttp.openai.OpenAI
import sttp.openai.OpenAIExceptions.OpenAIException
import sttp.openai.json.SttpUpickleApiExtension.deserializeJsonSnake
import sttp.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse
import sttp.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse.DoneEvent
import sttp.openai.requests.completions.chat.ChatRequestBody.ChatBody

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
