package sttp.ai.gemini.models

/** Well-known Gemini model identifiers. Use [[GeminiModel.CustomModel]] for models not listed here. */
sealed abstract class GeminiModel(val value: String)

object GeminiModel {
  case object Gemini25Pro extends GeminiModel("gemini-2.5-pro")
  case object Gemini25Flash extends GeminiModel("gemini-2.5-flash")
  case object Gemini25FlashLite extends GeminiModel("gemini-2.5-flash-lite")
  case class CustomModel(override val value: String) extends GeminiModel(value)

  val values: List[GeminiModel] = List(Gemini25Pro, Gemini25Flash, Gemini25FlashLite)

  def fromString(s: String): GeminiModel =
    values.find(_.value == s).getOrElse(CustomModel(s))
}
