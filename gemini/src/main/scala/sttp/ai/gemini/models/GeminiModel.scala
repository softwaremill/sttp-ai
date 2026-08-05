package sttp.ai.gemini.models

import sttp.ai.core.model.{AIModel, Capability}
import sttp.ai.core.model.Capability._

/** Well-known Gemini model identifiers. Use [[GeminiModel.CustomModel]] for models not listed here. */
sealed abstract class GeminiModel(val value: String) extends AIModel

object GeminiModel {
  case object Gemini36Flash extends GeminiModel("gemini-3.6-flash") with All
  case object Gemini35Flash extends GeminiModel("gemini-3.5-flash") with All
  case object Gemini35FlashLite extends GeminiModel("gemini-3.5-flash-lite") with All
  case object Gemini31FlashLite extends GeminiModel("gemini-3.1-flash-lite") with All
  case object Gemini25Pro extends GeminiModel("gemini-2.5-pro") with All
  case object Gemini25Flash extends GeminiModel("gemini-2.5-flash") with All
  case object Gemini25FlashLite extends GeminiModel("gemini-2.5-flash-lite") with All

  /** A model id not in the predefined list. Claims all capabilities — using it asserts your model supports what you use it for. */
  case class CustomModel(override val value: String) extends GeminiModel(value) with All

  val values: List[GeminiModel] =
    List(Gemini36Flash, Gemini35Flash, Gemini35FlashLite, Gemini31FlashLite, Gemini25Pro, Gemini25Flash, Gemini25FlashLite)

  def fromString(s: String): GeminiModel =
    values.find(_.value == s).getOrElse(CustomModel(s))
}
