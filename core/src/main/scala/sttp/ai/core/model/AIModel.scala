package sttp.ai.core.model

/** Base trait for provider model identifiers (OpenAI's `ChatCompletionModel`, `ClaudeModel`, `GeminiModel`).
  *
  * Model constants mix in [[Capability]] marker traits describing what the model supports; agent APIs require the relevant capability via
  * [[Supports]] evidence, so invalid model/task pairings are rejected at compile time.
  */
trait AIModel {

  /** The model identifier sent to the API, e.g. "gpt-4o". */
  def value: String
}
