package sttp.ai.openai.requests.completions

import sttp.ai.openai.requests.completions.CompletionsRequestBody.CompletionModel

object CompletionsResponseData {
  case class Choices(
      text: String,
      index: Int,
      finishReason: String,
      logprobs: Option[String] = None
  )

  case class CompletionsResponse(
      id: String,
      `object`: String,
      created: Int,
      model: CompletionModel,
      choices: Seq[Choices],
      usage: Usage
  )
}
