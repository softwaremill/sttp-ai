package sttp.ai.claude.models

import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}

sealed abstract class ClaudeModel(val value: String) {
  override def toString: String = value
}

object ClaudeModel {
  case object Claude3_5Sonnet extends ClaudeModel("claude-3-5-sonnet-20241022")
  case object Claude3_5SonnetLatest extends ClaudeModel("claude-3-5-sonnet-latest")
  case object Claude3_5Haiku extends ClaudeModel("claude-3-5-haiku-20241022")
  case object Claude3_5HaikuLatest extends ClaudeModel("claude-3-5-haiku-latest")
  case object Claude3Opus extends ClaudeModel("claude-3-opus-20240229")
  case object Claude3Sonnet extends ClaudeModel("claude-3-sonnet-20240229")
  case object Claude3Haiku extends ClaudeModel("claude-3-haiku-20240307")

  val values: Set[ClaudeModel] = Set(
    Claude3_5Sonnet,
    Claude3_5SonnetLatest,
    Claude3_5Haiku,
    Claude3_5HaikuLatest,
    Claude3Opus,
    Claude3Sonnet,
    Claude3Haiku
  )

  def fromString(value: String): Option[ClaudeModel] = values.find(_.value == value)

  implicit val rw: ReadWriter[ClaudeModel] = ReadWriter.merge(
    macroRW[Claude3_5Sonnet.type],
    macroRW[Claude3_5SonnetLatest.type],
    macroRW[Claude3_5Haiku.type],
    macroRW[Claude3_5HaikuLatest.type],
    macroRW[Claude3Opus.type],
    macroRW[Claude3Sonnet.type],
    macroRW[Claude3Haiku.type]
  )
}
