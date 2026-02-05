package sttp.ai.claude.models

import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}
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

  @key("thinking")
  case class ThinkingContent(thinking: String) extends ContentBlock {
    val `type`: String = "thinking"
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

  def text(content: String): TextContent = TextContent(content)

  def thinking(content: String): ThinkingContent = ThinkingContent(content)

  def image(mediaType: String, data: String): ImageContent =
    ImageContent(ImageSource.base64(mediaType, data))

  implicit val textContentRW: ReadWriter[TextContent] = macroRW
  implicit val thinkingContentRW: ReadWriter[ThinkingContent] = macroRW
  implicit val imageContentRW: ReadWriter[ImageContent] = macroRW
  implicit val toolUseContentRW: ReadWriter[ToolUseContent] = macroRW
  implicit val toolResultContentRW: ReadWriter[ToolResultContent] = macroRW

  implicit val rw: ReadWriter[ContentBlock] = ReadWriter.merge(
    textContentRW,
    thinkingContentRW,
    imageContentRW,
    toolUseContentRW,
    toolResultContentRW
  )
}
