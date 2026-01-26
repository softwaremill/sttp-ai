package sttp.ai.claude.models

import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}

sealed abstract class ClaudeModel(val value: String, val supportsStructuredOutput: Boolean) {
  override def toString: String = value
}

object ClaudeModel {
  case object Claude3_5Sonnet extends ClaudeModel("claude-3-5-sonnet-20241022", supportsStructuredOutput = false)
  case object Claude3_5SonnetLatest extends ClaudeModel("claude-3-5-sonnet-latest", supportsStructuredOutput = false)
  case object Claude3_5Haiku extends ClaudeModel("claude-3-5-haiku-20241022", supportsStructuredOutput = false)
  case object Claude3_5HaikuLatest extends ClaudeModel("claude-3-5-haiku-latest", supportsStructuredOutput = false)
  case object Claude3Opus extends ClaudeModel("claude-3-opus-20240229", supportsStructuredOutput = false)
  case object Claude3Sonnet extends ClaudeModel("claude-3-sonnet-20240229", supportsStructuredOutput = false)
  case object Claude3Haiku extends ClaudeModel("claude-3-haiku-20240307", supportsStructuredOutput = false)

  case object ClaudeSonnet4_0 extends ClaudeModel("claude-sonnet-4-20250514", supportsStructuredOutput = false)
  case object ClaudeOpus4_0 extends ClaudeModel("claude-opus-4-20250514", supportsStructuredOutput = false)

  case object ClaudeOpus4_1 extends ClaudeModel("claude-opus-4-1-20250805", supportsStructuredOutput = true)

  case object ClaudeSonnet4_5 extends ClaudeModel("claude-sonnet-4-5-20250929", supportsStructuredOutput = true)
  case object ClaudeSonnet4_5Latest extends ClaudeModel("claude-sonnet-4-5", supportsStructuredOutput = true)
  case object ClaudeHaiku4_5 extends ClaudeModel("claude-haiku-4-5-20251001", supportsStructuredOutput = true)
  case object ClaudeHaiku4_5Latest extends ClaudeModel("claude-haiku-4-5", supportsStructuredOutput = true)
  case object ClaudeOpus4_5 extends ClaudeModel("claude-opus-4-5-20251101", supportsStructuredOutput = true)
  case object ClaudeOpus4_5Latest extends ClaudeModel("claude-opus-4-5", supportsStructuredOutput = true)

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
    ClaudeOpus4_5Latest
  )

  def fromString(value: String): Option[ClaudeModel] = values.find(_.value == value)

  def modelSupportsStructuredOutput(modelId: String): Boolean =
    fromString(modelId) match {
      case Some(model) => model.supportsStructuredOutput
      case None        => true // Default to supported for unknown/future models
    }

  implicit val rw: ReadWriter[ClaudeModel] = ReadWriter.merge(
    macroRW[Claude3_5Sonnet.type],
    macroRW[Claude3_5SonnetLatest.type],
    macroRW[Claude3_5Haiku.type],
    macroRW[Claude3_5HaikuLatest.type],
    macroRW[Claude3Opus.type],
    macroRW[Claude3Sonnet.type],
    macroRW[Claude3Haiku.type],
    macroRW[ClaudeSonnet4_0.type],
    macroRW[ClaudeOpus4_0.type],
    macroRW[ClaudeHaiku4_5.type],
    macroRW[ClaudeHaiku4_5Latest.type],
    macroRW[ClaudeOpus4_1.type],
    macroRW[ClaudeSonnet4_5.type],
    macroRW[ClaudeSonnet4_5Latest.type],
    macroRW[ClaudeOpus4_5.type],
    macroRW[ClaudeOpus4_5Latest.type]
  )
}
