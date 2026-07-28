package sttp.ai.gemini.models

case class GenerationConfig(
    maxOutputTokens: Option[Int] = None,
    temperature: Option[Double] = None,
    seed: Option[Int] = None,
    stopSequences: Option[List[String]] = None,
    thinkingLevel: Option[String] = None,
    toolChoice: Option[String] = None
)
