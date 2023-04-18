package sttp.openai.requests.completions.edit

import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.Usage

object EditRequestResponseData {

  case class EditResponse(
      `object`: String,
      created: Int,
      choices: Seq[Choices],
      usage: Usage
  )

  object EditResponse {
    implicit val editResponseRW: SnakePickle.ReadWriter[EditResponse] = SnakePickle.macroRW[EditResponse]
  }

  case class Choices(
      text: String,
      index: Int
  )

  object Choices {
    implicit val choicesRW: SnakePickle.ReadWriter[Choices] = SnakePickle.macroRW[Choices]
  }

}
