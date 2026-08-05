package sttp.ai.gemini.models

import sttp.ai.core.model.{AIModel, Capability}
import sttp.ai.core.model.Capability._

/** Well-known Gemini model identifiers. Use [[GeminiModel.CustomModel]] for models not listed here. */
sealed abstract class GeminiModel(val value: String) extends AIModel

object GeminiModel {
  case object Gemini36Flash extends GeminiModel("gemini-3.6-flash") with Vision with ToolCalling with StructuredOutput with Reasoning
  case object Gemini35Flash extends GeminiModel("gemini-3.5-flash") with Vision with ToolCalling with StructuredOutput with Reasoning
  case object Gemini35FlashLite
      extends GeminiModel("gemini-3.5-flash-lite")
      with Vision
      with ToolCalling
      with StructuredOutput
      with Reasoning
  case object Gemini31FlashLite
      extends GeminiModel("gemini-3.1-flash-lite")
      with Vision
      with ToolCalling
      with StructuredOutput
      with Reasoning
  case object Gemini25Pro extends GeminiModel("gemini-2.5-pro") with Vision with ToolCalling with StructuredOutput with Reasoning
  case object Gemini25Flash extends GeminiModel("gemini-2.5-flash") with Vision with ToolCalling with StructuredOutput with Reasoning
  case object Gemini25FlashLite
      extends GeminiModel("gemini-2.5-flash-lite")
      with Vision
      with ToolCalling
      with StructuredOutput
      with Reasoning

  /** A model id not in the predefined list. Claims all capabilities — using it asserts your model supports what you use it for. */
  case class CustomModel(override val value: String)
      extends GeminiModel(value)
      with Vision
      with ToolCalling
      with StructuredOutput
      with Reasoning

  val values: List[GeminiModel] =
    List(Gemini36Flash, Gemini35Flash, Gemini35FlashLite, Gemini31FlashLite, Gemini25Pro, Gemini25Flash, Gemini25FlashLite)

  def fromString(s: String): GeminiModel =
    values.find(_.value == s).getOrElse(CustomModel(s))
}
