package sttp.ai.claude.models

case class Usage(
    inputTokens: Int,
    outputTokens: Int
) {
  def totalTokens: Int = inputTokens + outputTokens
}
