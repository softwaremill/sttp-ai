package sttp.ai.gemini.models

case class SafetySetting(
    `type`: String,
    threshold: String,
    method: Option[String] = None
)
