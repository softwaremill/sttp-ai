package sttp.ai.gemini.responses

import sttp.ai.gemini.models.{Content, InteractionStatus, Step, Usage}

case class InteractionResponse(
    id: Option[String] = None,
    status: InteractionStatus,
    model: Option[String] = None,
    steps: List[Step] = List.empty,
    usage: Option[Usage] = None,
    created: Option[String] = None,
    updated: Option[String] = None
) {

  /** All text content of the last model_output step, concatenated. Empty string if the interaction produced no model output. */
  def outputText: String =
    steps.reverse
      .collectFirst { case Step.ModelOutput(content) =>
        content.collect { case Content.Text(text) => text }.mkString
      }
      .getOrElse("")

  /** All function calls the model requested, in order. */
  def functionCalls: List[Step.FunctionCall] =
    steps.collect { case fc: Step.FunctionCall => fc }
}
