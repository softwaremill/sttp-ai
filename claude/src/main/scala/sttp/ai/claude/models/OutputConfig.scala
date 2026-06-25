package sttp.ai.claude.models

case class OutputConfig(
    format: Option[OutputFormat] = None,
    effort: Option[Effort] = None
)
