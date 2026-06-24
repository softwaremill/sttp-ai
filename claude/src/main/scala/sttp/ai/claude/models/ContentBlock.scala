package sttp.ai.claude.models

import io.circe.Json

sealed trait ContentBlock

object ContentBlock {
  case class Text(text: String, citations: Option[List[Citation]] = None) extends ContentBlock

  case class Thinking(thinking: String) extends ContentBlock

  case class Image(source: ImageSource) extends ContentBlock

  case class ToolUse(
      id: String,
      name: String,
      input: Map[String, Json]
  ) extends ContentBlock

  case class ToolResult(
      toolUseId: String,
      content: String,
      isError: Option[Boolean] = None
  ) extends ContentBlock

  case class Document(
      source: DocumentSource,
      title: Option[String] = None,
      context: Option[String] = None,
      citations: Option[CitationsConfig] = None
  ) extends ContentBlock

  case class ServerToolUse(
      id: String,
      name: String,
      input: Map[String, Json]
  ) extends ContentBlock

  case class WebSearchToolResult(
      toolUseId: String,
      content: WebSearchToolResultBlock,
      caller: Option[Json] = None
  ) extends ContentBlock

  case class WebSearchResult(
      url: String,
      title: String,
      pageAge: Option[String] = None,
      encryptedContent: Option[String] = None
  )

  sealed trait WebSearchToolResultBlock

  object WebSearchToolResultBlock {
    case class Results(items: List[WebSearchResult]) extends WebSearchToolResultBlock

    case class Error(errorCode: String) extends WebSearchToolResultBlock

    val ErrorTypeValue = "web_search_tool_result_error"
  }

  sealed trait DocumentSource

  object DocumentSource {
    case class Text(data: String, mediaType: String) extends DocumentSource
  }

  case class CitationsConfig(enabled: Option[Boolean] = None)

  sealed trait ImageSource

  object ImageSource {

    case class Base64(mediaType: String, data: String) extends ImageSource

    case class Url(url: String) extends ImageSource

    def base64(mediaType: String, data: String): ImageSource = Base64(
      mediaType = mediaType,
      data = data
    )

    def url(url: String): ImageSource = Url(url)
  }

  def text(content: String): Text = Text(content)

  def thinking(content: String): Thinking = Thinking(content)

  def image(mediaType: String, data: String): Image =
    Image(ImageSource.base64(mediaType, data))

  def document(text: String, title: Option[String] = None): Document =
    Document(
      source = DocumentSource.Text(text, "text/plain"),
      title = title,
      citations = Some(CitationsConfig(Some(true)))
    )
}
