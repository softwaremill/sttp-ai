package sttp.openai.streaming

import _root_.fs2.{Pipe, RaiseThrowable, Stream}
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.StreamRequest
import sttp.client4.impl.fs2.Fs2ServerSentEvents
import sttp.model.ResponseMetadata
import sttp.model.sse.ServerSentEvent
import sttp.openai.OpenAI
import sttp.openai.OpenAIExceptions.OpenAIException
import sttp.openai.json.SttpUpickleApiExtension.deserializeJsonSnake
import sttp.openai.requests.audio.speech.SpeechRequestBody
import sttp.openai.requests.completions.chat.ChatChunkRequestResponseData.ChatChunkResponse
import sttp.openai.requests.completions.chat.ChatRequestBody.ChatBody

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
}
