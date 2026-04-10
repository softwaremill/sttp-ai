package sttp.ai.claude.responses

import sttp.ai.claude.models._
import sttp.ai.core.json.SnakePickle._
import upickle.implicits._

sealed trait MessageStreamResponse {
  def `type`: String
}

object MessageStreamResponse {
  @key("message_start")
  case class MessageStart(
      message: MessageStartData
  ) extends MessageStreamResponse {
    val `type`: String = "message_start"
  }

  @key("content_block_start")
  case class ContentBlockStart(
      index: Int,
      contentBlock: ContentBlock
  ) extends MessageStreamResponse {
    val `type`: String = "content_block_start"
  }

  @key("content_block_delta")
  case class ContentBlockDelta(
      index: Int,
      delta: ContentDelta
  ) extends MessageStreamResponse {
    val `type`: String = "content_block_delta"
  }

  @key("content_block_stop")
  case class ContentBlockStop(
      index: Int
  ) extends MessageStreamResponse {
    val `type`: String = "content_block_stop"
  }

  @key("message_delta")
  case class MessageDelta(
      delta: MessageDeltaData
  ) extends MessageStreamResponse {
    val `type`: String = "message_delta"
  }

  @key("message_stop")
  case class MessageStop() extends MessageStreamResponse {
    val `type`: String = "message_stop"
  }

  @key("ping")
  case class Ping() extends MessageStreamResponse {
    val `type`: String = "ping"
  }

  @key("error")
  case class Error(error: ErrorDetail) extends MessageStreamResponse {
    val `type`: String = "error"
  }

  // Data classes for nested objects
  // All optional fields are provided default value so upickle can handle compatibility more gracefully:
  // Certain providers/proxies instead of passing null valued fields, omit them entirely from the object
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
    @key("text_delta")
    case class TextDelta(text: String) extends ContentDelta {
      val `type`: String = "text_delta"
    }

    @key("input_json_delta")
    case class InputJsonDelta(partialJson: String) extends ContentDelta {
      val `type`: String = "input_json_delta"
    }

    @key("thinking_delta")
    case class ThinkingDelta(thinking: String) extends ContentDelta {
      val `type`: String = "thinking_delta"
    }

    @key("signature_delta")
    case class SignatureDelta(signature: String) extends ContentDelta {
      val `type`: String = "signature_delta"
    }

    @key("citations_delta")
    case class CitationsDelta(citation: Citation) extends ContentDelta {
      val `type`: String = "citations_delta"
    }

    implicit val textDeltaRW: ReadWriter[TextDelta] = macroRW
    implicit val inputJsonDeltaRW: ReadWriter[InputJsonDelta] = macroRW
    implicit val thinkingDeltaRW: ReadWriter[ThinkingDelta] = macroRW
    implicit val signatureDeltaRW: ReadWriter[SignatureDelta] = macroRW
    implicit val citationsDeltaRW: ReadWriter[CitationsDelta] = macroRW

    implicit val rw: ReadWriter[ContentDelta] = ReadWriter.merge(
      textDeltaRW,
      inputJsonDeltaRW,
      thinkingDeltaRW,
      signatureDeltaRW,
      citationsDeltaRW
    )
  }

  case class MessageDeltaData(
      stopReason: Option[String] = None,
      stopSequence: Option[String] = None,
      usage: Option[Usage] = None
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
