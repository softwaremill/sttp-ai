package sttp.ai.core.agent

import io.circe.{Decoder, Encoder}
import sttp.tapir.{Schema => TapirSchema}

import scala.reflect.ClassTag

/** One member of a [[ResponseSchema.oneOf]] union: its model-facing name (the value of the `kind` discriminator), and the instances needed
  * to render its schema and round-trip its values. Build with [[Variant.apply]] (name = the runtime class's simple name) or
  * [[Variant.named]] (custom model-facing label).
  */
final class Variant[A] private (
    private[agent] val name: String,
    private[agent] val tapirSchema: TapirSchema[A],
    private[agent] val encoder: Encoder[A],
    private[agent] val decoder: Decoder[A],
    private[agent] val runtimeClass: Class[_]
)

object Variant {

  def apply[A](implicit s: TapirSchema[A], e: Encoder[A], d: Decoder[A], ct: ClassTag[A]): Variant[A] =
    named(defaultName(ct.runtimeClass))

  def named[A](name: String)(implicit s: TapirSchema[A], e: Encoder[A], d: Decoder[A], ct: ClassTag[A]): Variant[A] =
    new Variant[A](name, s, e, d, ct.runtimeClass)

  private def defaultName(cls: Class[_]): String = cls.getSimpleName.stripSuffix("$")
}
