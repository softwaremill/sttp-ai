package sttp.ai.gemini.models

import io.circe.Json

/** A single execution step of an interaction — both in request `input` (conversation replay) and response `steps`. Discriminated on the
  * wire by a `type` field (`user_input`, `model_output`, `function_call`, `function_result`).
  */
sealed trait Step

object Step {
  case class UserInput(content: List[Content]) extends Step
  case class ModelOutput(content: List[Content]) extends Step
  case class FunctionCall(id: String, name: String, arguments: Json) extends Step
  case class FunctionResult(callId: String, name: String, result: Json) extends Step

  def userText(text: String): Step = UserInput(List(Content.Text(text)))
}
