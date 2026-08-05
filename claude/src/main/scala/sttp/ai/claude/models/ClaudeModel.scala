package sttp.ai.claude.models

import sttp.ai.core.model.{AIModel, Capability}
import sttp.ai.core.model.Capability._

sealed abstract class ClaudeModel(val value: String) extends AIModel {
  override def toString: String = value
}

object ClaudeModel {

  case object Claude3_5Sonnet extends ClaudeModel("claude-3-5-sonnet-20241022") with Vision with ToolCalling
  case object Claude3_5SonnetLatest extends ClaudeModel("claude-3-5-sonnet-latest") with Vision with ToolCalling
  case object Claude3_5Haiku extends ClaudeModel("claude-3-5-haiku-20241022") with ToolCalling
  case object Claude3_5HaikuLatest extends ClaudeModel("claude-3-5-haiku-latest") with ToolCalling
  case object Claude3Opus extends ClaudeModel("claude-3-opus-20240229") with Vision with ToolCalling
  case object Claude3Sonnet extends ClaudeModel("claude-3-sonnet-20240229") with Vision with ToolCalling
  case object Claude3Haiku extends ClaudeModel("claude-3-haiku-20240307") with Vision with ToolCalling

  case object ClaudeSonnet4_0 extends ClaudeModel("claude-sonnet-4-20250514") with Vision with ToolCalling with Reasoning
  case object ClaudeOpus4_0 extends ClaudeModel("claude-opus-4-20250514") with Vision with ToolCalling with Reasoning

  case object ClaudeOpus4_1
      extends ClaudeModel("claude-opus-4-1-20250805")
      with Vision
      with ToolCalling
      with StructuredOutput
      with Reasoning

  case object ClaudeSonnet4_5
      extends ClaudeModel("claude-sonnet-4-5-20250929")
      with Vision
      with ToolCalling
      with StructuredOutput
      with Reasoning
  case object ClaudeSonnet4_5Latest
      extends ClaudeModel("claude-sonnet-4-5")
      with Vision
      with ToolCalling
      with StructuredOutput
      with Reasoning
  case object ClaudeHaiku4_5
      extends ClaudeModel("claude-haiku-4-5-20251001")
      with Vision
      with ToolCalling
      with StructuredOutput
      with Reasoning
  case object ClaudeHaiku4_5Latest extends ClaudeModel("claude-haiku-4-5") with Vision with ToolCalling with StructuredOutput with Reasoning
  case object ClaudeOpus4_5
      extends ClaudeModel("claude-opus-4-5-20251101")
      with Vision
      with ToolCalling
      with StructuredOutput
      with Reasoning
  case object ClaudeOpus4_5Latest extends ClaudeModel("claude-opus-4-5") with Vision with ToolCalling with StructuredOutput with Reasoning

  case object ClaudeSonnet5 extends ClaudeModel("claude-sonnet-5") with Vision with ToolCalling with StructuredOutput with Reasoning
  case object ClaudeOpus5 extends ClaudeModel("claude-opus-5") with Vision with ToolCalling with StructuredOutput with Reasoning

  /** A model id not in the predefined list. Claims all capabilities — using it asserts your model supports what you use it for. */
  case class CustomClaudeModel(override val value: String)
      extends ClaudeModel(value)
      with Vision
      with ToolCalling
      with StructuredOutput
      with Reasoning

  val values: Set[ClaudeModel] = Set(
    Claude3_5Sonnet,
    Claude3_5SonnetLatest,
    Claude3_5Haiku,
    Claude3_5HaikuLatest,
    Claude3Opus,
    Claude3Sonnet,
    Claude3Haiku,
    ClaudeSonnet4_0,
    ClaudeOpus4_0,
    ClaudeOpus4_1,
    ClaudeHaiku4_5,
    ClaudeHaiku4_5Latest,
    ClaudeSonnet4_5,
    ClaudeSonnet4_5Latest,
    ClaudeOpus4_5,
    ClaudeOpus4_5Latest,
    ClaudeSonnet5,
    ClaudeOpus5
  )

  def fromString(value: String): Option[ClaudeModel] = values.find(_.value == value)

  /** Whether the model behind `modelId` supports structured output. Unknown/future models default to supported. */
  def modelSupportsStructuredOutput(modelId: String): Boolean =
    fromString(modelId).forall(_.isInstanceOf[Capability.StructuredOutput])
}
