package sttp.ai.claude.models

import sttp.ai.claude.json.SnakePickle.{macroRW, ReadWriter}
import ujson.Value
import upickle.implicits.key

sealed trait ContentBlock {
  def `type`: String
}

object ContentBlock {
  @key("text")
  case class TextContent(text: String) extends ContentBlock {
    val `type`: String = "text"
  }

  @key("image")
  case class ImageContent(source: ImageSource) extends ContentBlock {
    val `type`: String = "image"
  }

  @key("tool_use")
  case class ToolUseContent(
      id: String,
      name: String,
      input: Map[String, Value]
  ) extends ContentBlock {
    val `type`: String = "tool_use"
  }

  @key("tool_result")
  case class ToolResultContent(
      toolUseId: String,
      content: String,
      isError: Option[Boolean] = None
  ) extends ContentBlock {
    val `type`: String = "tool_result"
  }

  case class ImageSource(
      `type`: String,
      mediaType: String,
      data: String
  )

  object ImageSource {
    def base64(mediaType: String, data: String): ImageSource = ImageSource(
      `type` = "base64",
      mediaType = mediaType,
      data = data
    )

    implicit val rw: ReadWriter[ImageSource] = macroRW
  }

  implicit val textContentRW: ReadWriter[TextContent] = macroRW
  implicit val imageContentRW: ReadWriter[ImageContent] = macroRW
  implicit val toolUseContentRW: ReadWriter[ToolUseContent] = macroRW
  implicit val toolResultContentRW: ReadWriter[ToolResultContent] = macroRW

  implicit val rw: ReadWriter[ContentBlock] = ReadWriter.merge(
    textContentRW,
    imageContentRW,
    toolUseContentRW,
    toolResultContentRW
  )
}
