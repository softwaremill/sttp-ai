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

  // Derives the name from getName rather than getSimpleName: the latter is platform- and version-divergent for
  // local/nested classes (JVM 2.13 says "Strict$1", JVM 3 "Strict", Scala Native just "1"). getName is identical
  // everywhere; the last non-numeric '$'-segment is the declared class name (numeric segments are local-class
  // counters, a trailing "$" marks a module class).
  private def defaultName(cls: Class[_]): String = {
    val base = cls.getName
    val afterPkg = base.substring(base.lastIndexOf('.') + 1)
    val segments = afterPkg.split('$').filter(s => s.nonEmpty && !s.forall(_.isDigit))
    if (segments.nonEmpty) segments.last else afterPkg
  }
}
