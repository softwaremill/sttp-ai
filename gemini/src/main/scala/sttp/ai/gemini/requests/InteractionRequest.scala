package sttp.ai.gemini.requests

import sttp.ai.gemini.models.{GenerationConfig, InteractionInput, ResponseFormat, SafetySetting, Tool}

case class InteractionRequest(
    model: String,
    input: InteractionInput,
    systemInstruction: Option[String] = None,
    tools: Option[List[Tool]] = None,
    responseFormat: Option[ResponseFormat] = None,
    generationConfig: Option[GenerationConfig] = None,
    previousInteractionId: Option[String] = None,
    store: Option[Boolean] = None,
    stream: Option[Boolean] = None,
    background: Option[Boolean] = None,
    safetySettings: Option[List[SafetySetting]] = None
) {
  def usesStructuredOutput: Boolean = responseFormat.exists {
    case ResponseFormat.JsonSchema(_) => true
    case ResponseFormat.Text          => false
  }

  def withStructuredOutput(format: ResponseFormat): InteractionRequest =
    this.copy(responseFormat = Some(format))
}

object InteractionRequest {
  def simple(model: String, text: String): InteractionRequest =
    InteractionRequest(model = model, input = InteractionInput.TextInput(text))

  def withSystem(model: String, system: String, text: String): InteractionRequest =
    InteractionRequest(model = model, input = InteractionInput.TextInput(text), systemInstruction = Some(system))

  def withTools(model: String, input: InteractionInput, tools: List[Tool]): InteractionRequest =
    InteractionRequest(model = model, input = input, tools = Some(tools))
}
