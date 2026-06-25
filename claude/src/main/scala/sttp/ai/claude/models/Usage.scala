package sttp.ai.claude.models

import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}

case class Usage(
    inputTokens: Int,
    outputTokens: Int,
    cacheReadInputTokens: Option[Int] = None,
    cacheCreationInputTokens: Option[Int] = None
) {
  def totalInputTokens: Int = inputTokens + cacheReadInputTokens.getOrElse(0) + cacheCreationInputTokens.getOrElse(0)
  def totalTokens: Int = totalInputTokens + outputTokens
}

object Usage {
  implicit val rw: ReadWriter[Usage] = macroRW
}
