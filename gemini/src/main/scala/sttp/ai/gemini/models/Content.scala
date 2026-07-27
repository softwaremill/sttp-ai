package sttp.ai.gemini.models

/** A single content item within an interaction step. Discriminated on the wire by a `type` field. */
sealed trait Content

object Content {
  case class Text(text: String) extends Content

  case class Image(
      data: Option[String] = None,
      uri: Option[String] = None,
      mimeType: Option[String] = None
  ) extends Content

  case class Audio(
      data: Option[String] = None,
      uri: Option[String] = None,
      mimeType: Option[String] = None
  ) extends Content

  case class Video(
      data: Option[String] = None,
      uri: Option[String] = None,
      mimeType: Option[String] = None
  ) extends Content

  case class Document(
      data: Option[String] = None,
      uri: Option[String] = None,
      mimeType: Option[String] = None
  ) extends Content
}
