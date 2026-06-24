package sttp.ai.openai.requests.completions.chat.message

sealed trait Content

object Content {
  case class TextContent(value: String) extends Content
  case class ArrayContent(value: Seq[ContentPart]) extends Content

  sealed trait ContentPart
  object ContentPart {
    case class Text(text: String) extends ContentPart

    case class ImageUrl(imageUrl: ImageUrlDetails) extends ContentPart
  }
  case class ImageUrlDetails(url: String, detail: Option[String] = None)
}
