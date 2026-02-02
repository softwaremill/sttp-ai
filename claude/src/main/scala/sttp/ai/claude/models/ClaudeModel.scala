package sttp.ai.claude.models

import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}

sealed trait ClaudeModel {
  val value: String
  override def toString: String = value
}

object ClaudeModel {
  sealed abstract class Default(val value: String) extends ClaudeModel
  sealed abstract class WithStructuredOutput(val value: String) extends ClaudeModel

  case object Claude3_5Sonnet extends Default("claude-3-5-sonnet-20241022")
  case object Claude3_5SonnetLatest extends Default("claude-3-5-sonnet-latest")
  case object Claude3_5Haiku extends Default("claude-3-5-haiku-20241022")
  case object Claude3_5HaikuLatest extends Default("claude-3-5-haiku-latest")
  case object Claude3Opus extends Default("claude-3-opus-20240229")
  case object Claude3Sonnet extends Default("claude-3-sonnet-20240229")
  case object Claude3Haiku extends Default("claude-3-haiku-20240307")

  case object ClaudeSonnet4_0 extends Default("claude-sonnet-4-20250514")
  case object ClaudeOpus4_0 extends Default("claude-opus-4-20250514")

  case object ClaudeOpus4_1 extends WithStructuredOutput("claude-opus-4-1-20250805")

  case object ClaudeSonnet4_5 extends WithStructuredOutput("claude-sonnet-4-5-20250929")
  case object ClaudeSonnet4_5Latest extends WithStructuredOutput("claude-sonnet-4-5")
  case object ClaudeHaiku4_5 extends WithStructuredOutput("claude-haiku-4-5-20251001")
  case object ClaudeHaiku4_5Latest extends WithStructuredOutput("claude-haiku-4-5")
  case object ClaudeOpus4_5 extends WithStructuredOutput("claude-opus-4-5-20251101")
  case object ClaudeOpus4_5Latest extends WithStructuredOutput("claude-opus-4-5")

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
      case Some(_: WithStructuredOutput) => true
      case Some(_: Default)              => false
      case None                          => true // Defaults to supported for unknown/future models
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
