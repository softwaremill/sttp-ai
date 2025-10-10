package sttp.ai.core.client

import sttp.ai.core.error.AIException
import sttp.capabilities.Streams
import sttp.client4._
import java.io.InputStream

/** Base trait for AI client implementations
  *
  * This trait provides a common interface for AI API clients, capturing the shared patterns across OpenAI and Claude APIs.
  *
  * @tparam Req
  *   The request type for the main API call (e.g., ChatCompletionRequest, MessageRequest)
  * @tparam Resp
  *   The response type for the main API call (e.g., ChatCompletionResponse, MessageResponse)
  * @tparam E
  *   The exception type for error handling
  */
trait AIClient[Req, Resp, E <: AIException] {

  /** Base URL for the API */
  def baseUrl: String

  /** Create a request to the main API endpoint
    *
    * For OpenAI this is typically chat completions, for Claude it's messages.
    */
  def createRequest(request: Req): Request[Either[E, Resp]]

  /** List available models */
  def listModels(): Request[Either[E, _]]

  /** Create a streaming request returning a binary stream
    *
    * This is used for Server-Sent Events (SSE) streaming.
    */
  def createStreamingRequest[S](
      streams: Streams[S],
      request: Req
  ): StreamRequest[Either[E, streams.BinaryStream], S]

  /** Create a streaming request returning an InputStream
    *
    * This is a simpler alternative to binary streams for blocking I/O.
    */
  def createInputStreamRequest(request: Req): Request[Either[E, InputStream]]
}
