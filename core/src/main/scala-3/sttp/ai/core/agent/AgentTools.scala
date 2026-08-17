package sttp.ai.core.agent

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import sttp.shared.Identity
import sttp.tapir.SchemaType.{SProduct, SProductField}
import sttp.tapir.{FieldName, Schema as TapirSchema}

import scala.quoted.*

/** Derives a set of [[AgentTool]]s from the public methods of a service trait (or class).
  *
  * A method becomes a tool iff it is public, has exactly one (possibly empty) parameter list, and is not declared on
  * `Any`/`AnyRef`/`Object` (inherited trait methods are included). Parameterless accessors (`def foo: String`, `val`s) are skipped. The
  * tool name is the method name, JSON property names are the parameter names, and each method must carry a
  * [[sttp.tapir.Schema.annotations.description]] annotation (optionally also on parameters, for property descriptions). `Option` parameters
  * become non-required properties. Each parameter type needs given tapir `Schema`, circe `Decoder` and `Encoder` instances.
  *
  * Scala 3 only: on Scala 2.13 define tools individually with [[AgentTool.fromFunction]].
  */
object AgentTools {

  /** Derives tools from a synchronous service: every method must return `String`. */
  inline def derive[S](service: S): Seq[AgentTool[Identity, ?]] =
    deriveF[Identity, S](service)

  /** Derives tools from an effectful service: every method must return `F[String]`. */
  inline def deriveF[F[_], S](service: S): Seq[AgentTool[F, ?]] =
    ${ deriveImpl[F, S]('service) }

  private def deriveImpl[F[_]: Type, S: Type](service: Expr[S])(using Quotes): Expr[Seq[AgentTool[F, ?]]] = {
    import quotes.reflect.*

    val sTpe = TypeRepr.of[S]
    val descriptionSym = TypeRepr.of[TapirSchema.annotations.description].typeSymbol

    // Renders a type from bare symbol names (recursing into type arguments) rather than via `tpe.show`. When this
    // macro is exercised via scala.compiletime.testing.typeCheckErrors (see AgentToolsDeriveErrorsSpec), types can be
    // declared inside the typechecked snippet; `.show`ing such a type after other symbol lookups (methodMembers,
    // getAnnotation, ...) have run triggers a dotty CyclicReference in that harness. Recursing through symbol names
    // avoids forcing that printing while still preserving type arguments (e.g. `List[Foo]`, not just `List`).
    def renderType(t: TypeRepr): String = t.dealias match {
      case AppliedType(base, args) => s"${base.typeSymbol.name}[${args.map(renderType).mkString(", ")}]"
      case other                   => other.typeSymbol.name
    }

    def fail(msg: String): Nothing = report.errorAndAbort(s"AgentTools.derive[${renderType(sTpe)}]: $msg")

    def annotationTextOf(sym: Symbol): Option[String] =
      sym.getAnnotation(descriptionSym).map {
        case Apply(_, List(Literal(StringConstant(text))))              => text
        case Apply(_, List(NamedArg(_, Literal(StringConstant(text))))) => text
        case _ => fail(s"the @description annotation on '${sym.name}' must be given a string literal")
      }

    // Annotations are not inherited onto overriding symbols in Scala 3, so when `S` is inferred as an implementation
    // class (e.g. `AgentTools.derive(new WeatherImpl)`), `m` is the impl's method and carries no annotations of its
    // own even though the trait method it overrides does. Fall back through `allOverriddenSymbols`, first hit wins.
    def descriptionOf(sym: Symbol): Option[String] =
      (sym :: sym.allOverriddenSymbols.toList).view.flatMap(annotationTextOf).headOption

    // Same fallback for parameter descriptions: if the method's own parameter symbol has no annotation, check the
    // corresponding parameter (by position in the single term parameter list) of each overridden method.
    def paramDescriptionOf(m: Symbol, paramIndex: Int, p: Symbol): Option[String] = {
      def paramOfOverride(o: Symbol): Option[Symbol] =
        o.paramSymss.filterNot(_.exists(_.isTypeParam)).flatten.lift(paramIndex)
      (p :: m.allOverriddenSymbols.toList.flatMap(paramOfOverride)).view.flatMap(annotationTextOf).headOption
    }

    val excludedOwners = Set(defn.AnyClass, defn.AnyRefClass, defn.ObjectClass)
    val methods = sTpe.typeSymbol.methodMembers
      .filterNot(_.isClassConstructor)
      .filterNot(m => m.flags.is(Flags.Synthetic) || m.flags.is(Flags.Artifact))
      .filterNot(m => m.flags.is(Flags.Private) || m.flags.is(Flags.Protected) || m.privateWithin.isDefined)
      .filterNot(m => excludedOwners.contains(m.owner))
      .filterNot(m => m.allOverriddenSymbols.exists(o => excludedOwners.contains(o.owner)))
      .filterNot(_.name.contains("$"))
      .filterNot(m => m.flags.is(Flags.FieldAccessor)) // getters/setters are properties, not tools
      .filter(_.paramSymss.nonEmpty) // parameterless accessors are properties, not tools
      .sortBy(_.name)

    methods.groupBy(_.name).collect { case (n, ms) if ms.sizeIs > 1 => n }.toList.sorted match {
      case Nil        => ()
      case overloaded => fail(s"overloaded methods are not supported (duplicate tool names): ${overloaded.mkString(", ")}")
    }

    // Builds one tool for a method whose input type is Tup, the tuple of its parameter types. Tuple elements are
    // accessed positionally via Product, so no Tuple upper bound is needed on Tup.
    def buildTool[Tup: Type](
        m: Symbol,
        toolDescription: String,
        params: List[Symbol],
        paramTypes: List[TypeRepr]
    ): Expr[AgentTool[F, Tup]] = {
      val arity = params.size

      val fieldInstances = params.zip(paramTypes).zipWithIndex.map { case ((p, tpe), i) =>
        tpe.asType match {
          case '[ft] =>
            def missing(what: String): Nothing =
              fail(s"no given $what for parameter '${p.name}' of method '${m.name}'")
            // `renderType` rather than `.show`, for the same CyclicReference reason as in `fail` above.
            val tpeName = renderType(tpe)
            val schema = Expr.summon[TapirSchema[ft]].getOrElse(missing(s"sttp.tapir.Schema[$tpeName]"))
            val decoder = Expr.summon[Decoder[ft]].getOrElse(missing(s"io.circe.Decoder[$tpeName]"))
            val encoder = Expr.summon[Encoder[ft]].getOrElse(missing(s"io.circe.Encoder[$tpeName]"))
            val described: Expr[TapirSchema[ft]] =
              paramDescriptionOf(m, i, p).fold(schema)(d => '{ $schema.description(${ Expr(d) }) })
            val idx = Expr(i)
            val field: Expr[SProductField[Tup]] = '{
              SProductField[Tup, ft](
                FieldName(${ Expr(p.name) }),
                $described,
                (t: Tup) => Some(t.asInstanceOf[Product].productElement($idx).asInstanceOf[ft])
              )
            }
            (Expr(p.name), field, '{ $decoder: Decoder[?] }, '{ $encoder: Encoder[?] })
        }
      }

      val namesExpr = Expr.ofList(fieldInstances.map(_._1))
      val fieldsExpr = Expr.ofList(fieldInstances.map(_._2))
      val decodersExpr = Expr.ofList(fieldInstances.map(_._3))
      val encodersExpr = Expr.ofList(fieldInstances.map(_._4))

      val schemaExpr: Expr[TapirSchema[Tup]] = '{ TapirSchema(SProduct[Tup]($fieldsExpr)) }

      val codecExpr: Expr[Codec[Tup]] = '{
        new Codec[Tup] {
          private val names = $namesExpr
          private val decoders = $decodersExpr
          private val encoders = $encodersExpr

          override def apply(c: io.circe.HCursor): Decoder.Result[Tup] = {
            val values = new Array[Any](${ Expr(arity) })
            var i = 0
            var failure: DecodingFailure = null
            while (i < values.length && (failure eq null)) {
              decoders(i).tryDecode(c.downField(names(i))) match {
                case Right(v) => values(i) = v
                case Left(f)  => failure = f
              }
              i += 1
            }
            if (failure ne null) Left(failure) else Right(Tuple.fromArray(values).asInstanceOf[Tup])
          }

          override def apply(t: Tup): Json = {
            val fields = names.iterator
              .zip(t.asInstanceOf[Product].productIterator)
              .zip(encoders.iterator)
              .map { case ((name, value), enc) => name -> enc.asInstanceOf[Encoder[Any]](value) }
              .filterNot(_._2.isNull)
            Json.fromJsonObject(JsonObject.fromIterable(fields.toList))
          }
        }
      }

      val execExpr: Expr[Tup => F[String]] = '{ (input: Tup) =>
        ${
          val argTerms = paramTypes.zipWithIndex.map { case (tpe, i) =>
            tpe.asType match {
              case '[ft] => '{ input.asInstanceOf[Product].productElement(${ Expr(i) }).asInstanceOf[ft] }.asTerm
            }
          }
          Apply(Select(service.asTerm, m), argTerms).asExprOf[F[String]]
        }
      }

      '{ AgentTool.fromFunctionF[F, Tup](${ Expr(m.name) }, ${ Expr(toolDescription) })($execExpr)(using $schemaExpr, $codecExpr) }
    }

    val toolExprs: List[Expr[AgentTool[F, ?]]] = methods.map { m =>
      if (m.paramSymss.exists(_.exists(_.isTypeParam)))
        fail(s"method '${m.name}' has type parameters, which are not supported")

      val termParamLists = m.paramSymss.filterNot(_.exists(_.isTypeParam))
      if (termParamLists.sizeIs > 1)
        fail(s"method '${m.name}' has multiple parameter lists, which are not supported")

      val params = termParamLists.headOption.getOrElse(Nil)
      if (params.exists(p => p.flags.is(Flags.Given) || p.flags.is(Flags.Implicit)))
        fail(s"method '${m.name}' has implicit/using parameters, which are not supported")
      if (params.exists(_.flags.is(Flags.HasDefault)))
        fail(s"method '${m.name}' has default parameter values, which are not supported")

      val toolDescription = descriptionOf(m).getOrElse(
        fail(s"method '${m.name}' is missing a @description annotation (sttp.tapir.Schema.annotations.description)")
      )

      val (paramTypes, resultType) = sTpe.memberType(m) match {
        case MethodType(_, tps, res) => (tps, res)
        case other                   => fail(s"method '${m.name}' has an unsupported shape: ${other.show}")
      }

      if (!(resultType =:= TypeRepr.of[F[String]]))
        fail(s"method '${m.name}' must return ${TypeRepr.of[F[String]].show}, but returns ${resultType.show}")

      val tupleTpe = paramTypes.foldRight(TypeRepr.of[EmptyTuple])((t, acc) => TypeRepr.of[*:].appliedTo(List(t, acc)))
      tupleTpe.asType match {
        case '[tup] => buildTool[tup](m, toolDescription, params, paramTypes)
      }
    }

    Expr.ofList(toolExprs)
  }
}
