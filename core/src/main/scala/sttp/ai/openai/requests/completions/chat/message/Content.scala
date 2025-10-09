package sttp.openai.requests.completions.chat.message

import sttp.openai.json.SnakePickle
import ujson._

sealed trait Content

object Content {
  case class TextContent(value: String) extends Content
  case class ArrayContent(value: Seq[ContentPart]) extends Content

  implicit val contentRW: SnakePickle.ReadWriter[Content] = SnakePickle
    .readwriter[Value]
    .bimap[Content](
      {
        case TextContent(value)  => SnakePickle.writeJs(value)
        case ArrayContent(value) => SnakePickle.writeJs(value)
      },
      jsonValue =>
        SnakePickle.read[Value](jsonValue) match {
          case Str(value) => TextContent(value)
          case Arr(value) => ArrayContent(value.toSeq.map(SnakePickle.read[ContentPart](_)))
          case e          => throw new Exception(s"Could not deserialize: $e")
        }
    )

  sealed trait ContentPart
  case class TextContentPart(text: String) extends ContentPart
  case class ImageContentPart(imageUrl: ImageUrl) extends ContentPart

  implicit val textContentPartRW: SnakePickle.ReadWriter[TextContentPart] =
    SnakePickle
      .readwriter[Value]
      .bimap[TextContentPart](
        textPart => Obj("type" -> "text", "text" -> textPart.text),
        json => TextContentPart(json("text").str)
      )

  implicit val imageContentPartRW: SnakePickle.ReadWriter[ImageContentPart] =
    SnakePickle
      .readwriter[Value]
      .bimap[ImageContentPart](
        imagePart => Obj("type" -> "image_url", "image_url" -> SnakePickle.writeJs(imagePart.imageUrl)),
        json => ImageContentPart(SnakePickle.read[ImageUrl](json("image_url")))
      )

  implicit val contentPartRW: SnakePickle.ReadWriter[ContentPart] =
    SnakePickle
      .readwriter[Value]
      .bimap[ContentPart](
        {
          case textPart: TextContentPart   => SnakePickle.writeJs(textPart)
          case imagePart: ImageContentPart => SnakePickle.writeJs(imagePart)
        },
        json =>
          json("type").str match {
            case "text"      => SnakePickle.read[TextContentPart](json)
            case "image_url" => SnakePickle.read[ImageContentPart](json)
          }
      )

  case class ImageUrl(url: String, detail: Option[String] = None)

  implicit val imageUrlRW: SnakePickle.ReadWriter[ImageUrl] = SnakePickle.macroRW[ImageUrl]
}
