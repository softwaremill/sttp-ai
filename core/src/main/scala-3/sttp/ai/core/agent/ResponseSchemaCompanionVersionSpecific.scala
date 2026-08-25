package sttp.ai.core.agent

import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema as TapirSchema

import scala.quoted.*
import scala.reflect.ClassTag

/** Scala 3-only additions to the [[ResponseSchema]] companion (the Scala 2.13 counterpart of this trait is empty). */
trait ResponseSchemaCompanionVersionSpecific {

  /** Derives a [[ResponseSchema]] for a Scala 3 union type (e.g. `Refund | Complaint | GeneralQuery`) by decomposing the union and
    * delegating to [[ResponseSchema.oneOf]] with one [[Variant]] per member — one engine, one wire shape. Each member needs given tapir
    * `Schema`, circe `Encoder`/`Decoder`, and a `ClassTag`. Members must be distinct case-class-like types (object schemas); the variant
    * name is the member class's simple name.
    *
    * Scala 3 only: on Scala 2.13 (or for sealed traits), list the variants explicitly with [[ResponseSchema.oneOf]].
    */
  inline def derivedUnion[T]: ResponseSchema[T] = ${ ResponseSchemaUnionMacros.deriveNoDescImpl[T] }

  inline def derivedUnion[T](description: String): ResponseSchema[T] = ${ ResponseSchemaUnionMacros.deriveDescImpl[T]('description) }
}

private[agent] object ResponseSchemaUnionMacros {

  def deriveNoDescImpl[T: Type](using Quotes): Expr[ResponseSchema[T]] = deriveImpl[T](None)

  def deriveDescImpl[T: Type](description: Expr[String])(using Quotes): Expr[ResponseSchema[T]] =
    deriveImpl[T](Some(description))

  private def deriveImpl[T: Type](description: Option[Expr[String]])(using Quotes): Expr[ResponseSchema[T]] = {
    import quotes.reflect.*

    def renderType(t: TypeRepr): String = MacroSupport.renderType(t)

    def fail(msg: String): Nothing = report.errorAndAbort(s"ResponseSchema.derivedUnion[${renderType(TypeRepr.of[T])}]: $msg")

    def flatten(t: TypeRepr): List[TypeRepr] = t.dealias match {
      case OrType(left, right) => flatten(left) ++ flatten(right)
      case other               => List(other)
    }

    val members = flatten(TypeRepr.of[T])
    if (members.sizeIs < 2)
      fail("the type argument must be a union type with at least two members; for a single type use ResponseSchema.derived")

    val _ = members.foldLeft(List.empty[TypeRepr]) { (seen, t) =>
      if (seen.exists(_ =:= t)) fail(s"duplicate union member ${renderType(t)}")
      t :: seen
    }

    // Distinct types sharing a simple name (e.g. billing.Refund | shipping.Refund) would collide at runtime on the kind
    // discriminator (and often on the erased class); the macro knows the names, so fail at compile time instead.
    members.groupBy(_.typeSymbol.name).collect { case (n, ts) if ts.sizeIs > 1 => (n, ts) }.toList match {
      case Nil        => ()
      case collisions =>
        // Instantiations of the SAME generic type erase to one runtime class: encoding cannot tell them apart, so no
        // labeling helps - a different remedy than for distinct types merely sharing a simple name.
        collisions.collectFirst { case (n, ts) if ts.map(_.typeSymbol).distinct.sizeIs == 1 => (n, ts) } match {
          case Some((n, ts)) =>
            fail(
              s"union members ${ts.map(renderType).mkString(" and ")} are instantiations of the same generic type '$n', " +
                "which erase to one runtime class; encoding cannot distinguish them - model them as distinct case classes instead"
            )
          case None =>
            val detail = collisions.map { case (n, ts) => s"'$n' (${ts.map(_.typeSymbol.fullName).mkString(", ")})" }.mkString("; ")
            fail(
              s"duplicate variant names across union members: $detail; distinct types sharing a simple name cannot be told apart " +
                "by the kind discriminator - use ResponseSchema.oneOf with Variant.named to label them explicitly"
            )
        }
    }

    // Union members must be case classes: primitives, Strings, and other non-product types render to non-object
    // schemas and would only fail at runtime construction; custom object schemas for non-case classes remain available
    // through the explicit ResponseSchema.oneOf.
    members.foreach { tpe =>
      val isCaseClass = tpe.classSymbol.exists(cs => cs.flags.is(Flags.Case) && !cs.flags.is(Flags.Module))
      if (!isCaseClass)
        fail(
          s"union member ${renderType(tpe)} is not a case class; variants must be case classes - " +
            "for custom object schemas use ResponseSchema.oneOf"
        )
    }

    val variantExprs: List[Expr[Variant[? <: T]]] = members.map { tpe =>
      tpe.asType match {
        case '[a] =>
          def missing(what: String): Nothing = fail(s"no given $what for union member ${renderType(tpe)}")
          val s = Expr.summon[TapirSchema[a]].getOrElse(missing(s"sttp.tapir.Schema[${renderType(tpe)}]"))
          val e = Expr.summon[Encoder[a]].getOrElse(missing(s"io.circe.Encoder[${renderType(tpe)}]"))
          val d = Expr.summon[Decoder[a]].getOrElse(missing(s"io.circe.Decoder[${renderType(tpe)}]"))
          val ct = Expr.summon[ClassTag[a]].getOrElse(missing(s"scala.reflect.ClassTag[${renderType(tpe)}]"))
          '{ Variant[a](using $s, $e, $d, $ct) }.asExprOf[Variant[? <: T]]
      }
    }

    val first = variantExprs.head
    val rest = Varargs(variantExprs.tail)
    description match {
      case None       => '{ ResponseSchema.oneOf[T]($first, $rest*) }
      case Some(desc) => '{ ResponseSchema.oneOf[T]($desc)($first, $rest*) }
    }
  }
}
