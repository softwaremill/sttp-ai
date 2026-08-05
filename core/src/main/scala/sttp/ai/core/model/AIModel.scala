package sttp.ai.core.model

import scala.annotation.implicitNotFound

/** Base trait for provider model identifiers (OpenAI's `ChatCompletionModel`, `ClaudeModel`, `GeminiModel`).
  *
  * Model constants mix in [[Capability]] marker traits describing what the model supports; agent APIs require the relevant capability via
  * [[Supports]] evidence, so invalid model/task pairings are rejected at compile time.
  */
trait AIModel {

  /** The model identifier sent to the API, e.g. "gpt-4o". */
  def value: String
}

/** Capability marker traits mixed into model constants. Pure markers — they carry no members, only type-level facts.
  *
  * Custom/unknown model classes (e.g. `CustomChatCompletionModel`) mix in all capabilities: using one asserts your model supports whatever
  * you use it for.
  */
object Capability {

  /** The model accepts image input. */
  trait Vision

  /** The model supports tool/function calling. */
  trait ToolCalling

  /** The model supports schema-constrained (structured) output. */
  trait StructuredOutput

  /** The model is a reasoning ("thinking") model. */
  trait Reasoning
}

/** Evidence that model type `M` declares capability `C`. Resolved automatically for any model constant that mixes in `C`. */
@implicitNotFound(
  "Model ${M} is not declared to support ${C}. Pick a model constant that mixes in ${C}, or use the provider's custom model class (which claims all capabilities)."
)
sealed trait Supports[M, C]

object Supports {
  private val instance: Supports[Any, Any] = new Supports[Any, Any] {}

  implicit def fromSubtype[M, C](implicit ev: M <:< C): Supports[M, C] =
    instance.asInstanceOf[Supports[M, C]]
}
