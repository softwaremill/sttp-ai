package sttp.openai.requests.completions.chat

import sttp.model.sse.ServerSentEvent
import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.Usage

object ChatChunkRequestResponseData {

  /** @param role
    *   The role of the author of this message.
    * @param content
    *   The contents of the message.
    * @param functionCall
    *   The name of the author of this message. May contain a-z, A-Z, 0-9, and underscores, with a maximum length of 64 characters.
    */
  case class Delta(
      role: Option[Role] = None,
      content: Option[String] = None,
      toolCalls: Seq[ToolCall] = Nil,
      functionCall: Option[FunctionCall] = None
  )

  object Delta {
    implicit val deltaR: SnakePickle.Reader[Delta] = SnakePickle.macroR[Delta]
  }

  case class Choices(
      delta: Delta,
      finishReason: Option[String] = None,
      index: Int
  )

  object Choices {
    implicit val choicesR: SnakePickle.Reader[Choices] = SnakePickle.macroR[Choices]
  }

  case class ChatChunkResponse(
      id: String,
      choices: Seq[Choices],
      created: Int,
      model: String,
      `object`: String,
      systemFingerprint: Option[String] = None,
      usage: Option[Usage] = None
  )

  object ChatChunkResponse {
    val DoneEventMessage = "[DONE]"
    val DoneEvent = ServerSentEvent(Some(DoneEventMessage))

    implicit val chunkChatR: SnakePickle.Reader[ChatChunkResponse] = SnakePickle.macroR[ChatChunkResponse]
  }

}
