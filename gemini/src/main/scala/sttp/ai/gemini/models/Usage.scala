package sttp.ai.gemini.models

case class Usage(
    totalInputTokens: Option[Long] = None,
    totalOutputTokens: Option[Long] = None,
    totalTokens: Option[Long] = None,
    totalCachedTokens: Option[Long] = None,
    totalThoughtTokens: Option[Long] = None,
    totalToolUseTokens: Option[Long] = None
)
