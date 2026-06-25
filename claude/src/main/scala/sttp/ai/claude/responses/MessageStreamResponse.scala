package sttp.ai.claude.responses

import sttp.ai.claude.models._

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

  case class MessageStartData(
      id: String,
      `type`: String,
      role: String,
      content: List[ContentBlock],
      model: String,
      stopReason: Option[String] = None,
      stopSequence: Option[String] = None,
      usage: Usage
  )

  sealed trait ContentDelta

  object ContentDelta {
    case class TextDelta(text: String) extends ContentDelta {
      val `type`: String = "text_delta"
    }

    case class InputJsonDelta(partialJson: String) extends ContentDelta {
      val `type`: String = "input_json_delta"
    }

    case class ThinkingDelta(thinking: String) extends ContentDelta {
      val `type`: String = "thinking_delta"
    }

    case class SignatureDelta(signature: String) extends ContentDelta {
      val `type`: String = "signature_delta"
    }

    case class CitationsDelta(citation: Citation) extends ContentDelta {
      val `type`: String = "citations_delta"
    }
  }

  case class MessageDeltaData(
      stopReason: Option[String] = None,
      stopSequence: Option[String] = None,
      usage: Option[Usage] = None
  )

  object EventData {
    val DoneEvent = "[DONE]"
  }
}
