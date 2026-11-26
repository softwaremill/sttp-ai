package sttp.ai.claude.models

import sttp.ai.core.model.AIModel
import sttp.ai.core.model.Capability._

sealed abstract class ClaudeModel(val value: String) extends AIModel {
  override def toString: String = value
}

object ClaudeModel {

  case object ClaudeHaiku4_5 extends ClaudeModel("claude-haiku-4-5-20251001") with All
  case object ClaudeHaiku4_5Latest extends ClaudeModel("claude-haiku-4-5") with All
  case object ClaudeOpus4_5 extends ClaudeModel("claude-opus-4-5-20251101") with All
  case object ClaudeOpus4_5Latest extends ClaudeModel("claude-opus-4-5") with All
  case object ClaudeOpus4_6 extends ClaudeModel("claude-opus-4-6") with All
  case object ClaudeSonnet4_6 extends ClaudeModel("claude-sonnet-4-6") with All
  case object ClaudeOpus4_7 extends ClaudeModel("claude-opus-4-7") with All
  case object ClaudeOpus4_8 extends ClaudeModel("claude-opus-4-8") with All

  case object ClaudeSonnet5 extends ClaudeModel("claude-sonnet-5") with All
  case object ClaudeOpus5 extends ClaudeModel("claude-opus-5") with All
  case object ClaudeSonnet5_5 extends ClaudeModel("claude-sonnet-5-5") with All
  case object ClaudeOpus5_5 extends ClaudeModel("claude-opus-5-5") with All
  case object ClaudeHaiku5_5 extends ClaudeModel("claude-haiku-5-5") with All

  case object ClaudeFable5 extends ClaudeModel("claude-fable-5") with All
  case object ClaudeFable5_1 extends ClaudeModel("claude-fable-5-1") with All
  case object ClaudeFable5_2 extends ClaudeModel("claude-fable-5-2") with All

  /** A model id not in the predefined list. Claims all capabilities — using it asserts your model supports what you use it for. */
  case class CustomClaudeModel(override val value: String) extends ClaudeModel(value) with All

  val values: Set[ClaudeModel] = Set(
    ClaudeHaiku4_5,
    ClaudeHaiku4_5Latest,
    ClaudeOpus4_5,
    ClaudeOpus4_5Latest,
    ClaudeOpus4_6,
    ClaudeSonnet4_6,
    ClaudeOpus4_7,
    ClaudeOpus4_8,
    ClaudeSonnet5,
    ClaudeOpus5,
    ClaudeSonnet5_5,
    ClaudeOpus5_5,
    ClaudeHaiku5_5,
    ClaudeFable5,
    ClaudeFable5_1,
    ClaudeFable5_2
  )

  def fromString(value: String): Option[ClaudeModel] = values.find(_.value == value)
}
