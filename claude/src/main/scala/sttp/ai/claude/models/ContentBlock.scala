package sttp.ai.claude.models

import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}
import ujson.Value
import upickle.implicits.key

sealed trait ContentBlock {
  def `type`: String
}

object ContentBlock {
  @key("text")
  case class TextContent(text: String, citations: Option[List[Citation]] = None) extends ContentBlock {
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

  @key("document")
  case class DocumentContent(
      source: DocumentSource,
      title: Option[String] = None,
      context: Option[String] = None,
      citations: Option[CitationsConfig] = None
  ) extends ContentBlock {
    val `type`: String = "document"
  }

  sealed trait DocumentSource {
    def `type`: String
  }

  object DocumentSource {
    @key("text")
    case class PlainTextSource(data: String, mediaType: String) extends DocumentSource {
      val `type`: String = "text"
    }

    implicit val plainTextSourceRW: ReadWriter[PlainTextSource] = macroRW
    implicit val rw: ReadWriter[DocumentSource] = ReadWriter.merge(plainTextSourceRW)
  }

  case class CitationsConfig(enabled: Option[Boolean] = None)

  object CitationsConfig {
    implicit val rw: ReadWriter[CitationsConfig] = macroRW
  }

  sealed trait ImageSource {
    def `type`: String
  }

  object ImageSource {

    @key("base64")
    case class Base64ImageSource(mediaType: String, data: String) extends ImageSource {
      val `type`: String = "base64"
    }

    @key("url")
    case class URLImageSource(url: String) extends ImageSource {
      val `type`: String = "url"
    }

    def base64(mediaType: String, data: String): ImageSource = Base64ImageSource(
      mediaType = mediaType,
      data = data
    )

    def url(url: String): ImageSource = URLImageSource(url)

    implicit val base64ImageSourceRW: ReadWriter[Base64ImageSource] = macroRW
    implicit val urlImageSourceRW: ReadWriter[URLImageSource] = macroRW
    implicit val rw: ReadWriter[ImageSource] = macroRW
  }

  def text(content: String): TextContent = TextContent(content)

  def thinking(content: String): ThinkingContent = ThinkingContent(content)

  def image(mediaType: String, data: String): ImageContent =
    ImageContent(ImageSource.base64(mediaType, data))

  def document(text: String, title: Option[String] = None): DocumentContent =
    DocumentContent(
      source = DocumentSource.PlainTextSource(text, "text/plain"),
      title = title,
      citations = Some(CitationsConfig(Some(true)))
    )

  implicit val textContentRW: ReadWriter[TextContent] = macroRW
  implicit val thinkingContentRW: ReadWriter[ThinkingContent] = macroRW
  implicit val imageContentRW: ReadWriter[ImageContent] = macroRW
  implicit val toolUseContentRW: ReadWriter[ToolUseContent] = macroRW
  implicit val toolResultContentRW: ReadWriter[ToolResultContent] = macroRW
  implicit val documentContentRW: ReadWriter[DocumentContent] = macroRW

  implicit val rw: ReadWriter[ContentBlock] = ReadWriter.merge(
    textContentRW,
    thinkingContentRW,
    imageContentRW,
    toolUseContentRW,
    toolResultContentRW,
    documentContentRW
  )
}
