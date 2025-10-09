package sttp.openai.requests.completions

import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.CompletionsRequestBody.CompletionModel

object CompletionsResponseData {
  case class Choices(
      text: String,
      index: Int,
      finishReason: String,
      logprobs: Option[String] = None
  )
  object Choices {
    implicit val choicesR: SnakePickle.Reader[Choices] = SnakePickle.macroR[Choices]
  }

  case class CompletionsResponse(
      id: String,
      `object`: String,
      created: Int,
      model: CompletionModel,
      choices: Seq[Choices],
      usage: Usage
  )
  object CompletionsResponse {
    implicit val choicesR: SnakePickle.Reader[CompletionsResponse] = SnakePickle.macroR[CompletionsResponse]
  }
}
