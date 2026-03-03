package sttp.ai.claude

import sttp.ai.claude.ClaudeExceptions.ClaudeException
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.{MessageResponse, MessageStreamResponse, ModelsResponse}
import sttp.ai.claude.ClaudeZioClient.ClaudeResponse
import sttp.ai.claude.streaming.zio.ClaudeZioStreaming.ClaudeClientZioExtension
import sttp.capabilities.zio.ZioStreams
import sttp.client4.impl.zio.ZioServerSentEvents
import sttp.client4.{Request, StreamRequest, WebSocketStreamBackend}
import sttp.model.ResponseMetadata
import sttp.model.sse.ServerSentEvent
import zio.{IO, Task, UIO, ZIO, ZLayer}
import zio.stream.Stream
import sttp.ai.core.json.SnakePickle._


class ClaudeZioClient private (
    client: ClaudeClient,
    backend: WebSocketStreamBackend[UIO, ZioStreams]
) {

  /** Creates a model response for the given message request.
    *
    * [[https://docs.anthropic.com/claude/reference/messages_post]]
    *
    * @param request
    *   Message request body.
    */
  def createMessage(request: MessageRequest): ClaudeResponse[MessageResponse] =
    send(client.createMessage(request))

  /** List the models available.
    *
    * [[https://docs.anthropic.com/claude/reference/models_list]]
    */
  def listModels(): ClaudeResponse[ModelsResponse] =
    send(client.listModels())

  /** Creates and streams a Claude message response as chunk objects for the given message request. The request will complete and the
    * connection close only once the source is fully consumed.
    *
    * [[https://docs.anthropic.com/claude/reference/messages_post]]
    *
    * @param messageRequest
    *   Message request body.
    */
  def createStreamedMessage(messageRequest: MessageRequest): ClaudeResponse[Stream[Throwable, MessageStreamResponse]] = {
    val request = client.createStreamedMessage(messageRequest)
    request.send(backend).flatMap(response => ZIO.fromEither(response.body))
  }

  private def send[A](request: Request[Either[ClaudeException, A]]): ClaudeResponse[A] =
    request.send(backend).flatMap(response => ZIO.fromEither(response.body))
}

object ClaudeZioClient {
  type ClaudeResponse[A] = IO[ClaudeException, A]

  val layer: ZLayer[ClaudeClient with WebSocketStreamBackend[UIO, ZioStreams], Nothing, ClaudeZioClient] = ZLayer {
    for {
      client  <- ZIO.service[ClaudeClient]
      backend <- ZIO.service[WebSocketStreamBackend[UIO, ZioStreams]]
    } yield new ClaudeZioClient(client, backend)
  }
}
