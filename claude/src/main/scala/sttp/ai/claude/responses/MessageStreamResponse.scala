package sttp.ai.claude.responses

import sttp.ai.claude.models.{ContentBlock, Usage}
import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}

sealed trait MessageStreamResponse {
  def `type`: String
}

object MessageStreamResponse {
  case class MessageStart(
      message: MessageStartData
  ) extends MessageStreamResponse {
    val `type`: String = "message_start"
  }

  case class ContentBlockStart(
      index: Int,
      contentBlock: ContentBlock
  ) extends MessageStreamResponse {
    val `type`: String = "content_block_start"
  }

  case class ContentBlockDelta(
      index: Int,
      delta: ContentDelta
  ) extends MessageStreamResponse {
    val `type`: String = "content_block_delta"
  }

  case class ContentBlockStop(
      index: Int
  ) extends MessageStreamResponse {
    val `type`: String = "content_block_stop"
  }

  case class MessageDelta(
      delta: MessageDeltaData
  ) extends MessageStreamResponse {
    val `type`: String = "message_delta"
  }

  case class MessageStop() extends MessageStreamResponse {
    val `type`: String = "message_stop"
  }

  case class Ping() extends MessageStreamResponse {
    val `type`: String = "ping"
  }

  case class Error(error: ErrorDetail) extends MessageStreamResponse {
    val `type`: String = "error"
  }

  // Data classes for nested objects
  case class MessageStartData(
      id: String,
      `type`: String,
      role: String,
      content: List[ContentBlock],
      model: String,
      stopReason: Option[String],
      stopSequence: Option[String],
      usage: Usage
  )

  sealed trait ContentDelta

  object ContentDelta {
    case class TextDelta(text: String) extends ContentDelta {
      val `type`: String = "text_delta"
    }

    implicit val textDeltaRW: ReadWriter[TextDelta] = macroRW
    implicit val rw: ReadWriter[ContentDelta] = ReadWriter.merge(textDeltaRW)
  }

  case class MessageDeltaData(
      stopReason: Option[String],
      stopSequence: Option[String],
      usage: Option[Usage]
  )

  // Companion object for event parsing
  object EventData {
    val DoneEvent = "[DONE]"
  }

  // ReadWriter instances
  implicit val messageStartDataRW: ReadWriter[MessageStartData] = macroRW
  implicit val messageDeltaDataRW: ReadWriter[MessageDeltaData] = macroRW

  implicit val messageStartRW: ReadWriter[MessageStart] = macroRW
  implicit val contentBlockStartRW: ReadWriter[ContentBlockStart] = macroRW
  implicit val contentBlockDeltaRW: ReadWriter[ContentBlockDelta] = macroRW
  implicit val contentBlockStopRW: ReadWriter[ContentBlockStop] = macroRW
  implicit val messageDeltaRW: ReadWriter[MessageDelta] = macroRW
  implicit val messageStopRW: ReadWriter[MessageStop] = macroRW
  implicit val pingRW: ReadWriter[Ping] = macroRW
  implicit val errorRW: ReadWriter[Error] = macroRW

  implicit val rw: ReadWriter[MessageStreamResponse] = ReadWriter.merge(
    messageStartRW,
    contentBlockStartRW,
    contentBlockDeltaRW,
    contentBlockStopRW,
    messageDeltaRW,
    messageStopRW,
    pingRW,
    errorRW
  )
}
