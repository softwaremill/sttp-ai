package sttp.ai.claude.models

import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}

case class OutputConfig(
    format: Option[OutputFormat] = None,
    effort: Option[Effort] = None
)

object OutputConfig {
  implicit val rw: ReadWriter[OutputConfig] = macroRW
}
