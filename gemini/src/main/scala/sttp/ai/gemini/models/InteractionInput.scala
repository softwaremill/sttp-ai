package sttp.ai.gemini.models

/** The `input` field of an interaction request: a plain string for simple prompts, or a list of steps for full conversation replay
  * (stateless mode, `store = false`).
  */
sealed trait InteractionInput

object InteractionInput {
  case class TextInput(value: String) extends InteractionInput
  case class StepsInput(steps: List[Step]) extends InteractionInput
}
