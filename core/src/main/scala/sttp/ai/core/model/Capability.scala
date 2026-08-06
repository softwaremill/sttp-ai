package sttp.ai.core.model

import scala.annotation.implicitNotFound

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

  /** Shorthand for a model that supports every capability. Mixing this in is equivalent to mixing in all four marker traits. */
  trait All extends Vision with ToolCalling with StructuredOutput with Reasoning
}

/** Evidence that model type `M` declares capability `C`. Resolved automatically for any model constant that mixes in `C`.
  *
  * Sealed by design: capabilities are declared by mixing the marker trait into the model type, not by providing ad-hoc instances. For
  * models outside the predefined constants, use the provider's custom model class, which claims all capabilities. If a predefined
  * constant's tag is missing or wrong (capability data is curated and can lag the providers), [[Supports.assume]] is the explicit,
  * per-capability opt-out.
  */
@implicitNotFound(
  "Model ${M} is not declared to support ${C}. Pick a model constant that mixes in ${C}, or use the provider's custom model class (which claims all capabilities)."
)
sealed trait Supports[M, C]

object Supports {
  private val instance: Supports[Any, Any] = new Supports[Any, Any] {}

  implicit def fromSubtype[M, C](implicit ev: M <:< C): Supports[M, C] =
    instance.asInstanceOf[Supports[M, C]]

  /** Explicitly asserts that model `M` supports capability `C`, bypassing the tag on the constant.
    *
    * Use this when a constant's curated capability tags are missing or wrong — it opts out of checking for exactly this model/capability
    * pair while keeping every other check intact (unlike falling back to a raw model-name string, which disables all checking):
    *
    * {{{
    * implicit val ev: Supports[ChatCompletionModel.SomeModel.type, Capability.StructuredOutput] = Supports.assume
    * }}}
    */
  def assume[M, C]: Supports[M, C] = instance.asInstanceOf[Supports[M, C]]
}
