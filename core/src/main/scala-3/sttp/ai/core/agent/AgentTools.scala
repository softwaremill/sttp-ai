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

    def renderType(t: TypeRepr): String = MacroSupport.renderType(t)

    def fail(msg: String): Nothing = report.errorAndAbort(s"AgentTools.derive[${renderType(sTpe)}]: $msg")

    // Accepts a string literal or any compile-time constant string (e.g. a `final val`), whose value lives in the
    // argument's ConstantType rather than in a Literal tree.
    def constText(t: Term): Option[String] = t match {
      case Literal(StringConstant(text)) => Some(text)
      case NamedArg(_, inner)            => constText(inner)
      case _                             =>
        t.tpe.widenTermRefByName.dealias match {
          case ConstantType(StringConstant(text)) => Some(text)
          case _                                  => None
        }
    }

    def annotationTextOf(sym: Symbol): Option[String] =
      sym.getAnnotation(descriptionSym).map {
        case Apply(_, List(arg)) =>
          constText(arg).getOrElse(fail(s"the @description annotation on '${sym.name}' must be given a constant string"))
        case _ => fail(s"the @description annotation on '${sym.name}' must be given a constant string")
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
    val visibleMembers = sTpe.typeSymbol.methodMembers
      .filterNot(_.isClassConstructor)
      .filterNot(m => m.flags.is(Flags.Synthetic) || m.flags.is(Flags.Artifact))
      .filterNot(m => m.flags.is(Flags.Private) || m.flags.is(Flags.Protected) || m.privateWithin.isDefined)
      .filterNot(m => excludedOwners.contains(m.owner))
      .filterNot(m => m.allOverriddenSymbols.exists(o => excludedOwners.contains(o.owner)))
      .filterNot(_.name.contains("$"))

    val methods = visibleMembers
      .filterNot(m => m.flags.is(Flags.FieldAccessor)) // getters/setters are properties, not tools
      .filter(_.paramSymss.nonEmpty) // parameterless accessors are properties, not tools
      .sortBy(_.name)

    // A parameterless def is skipped as a property, but one that carries @description signals clear intent to expose
    // a tool — fail loudly instead of silently dropping it.
    visibleMembers
      .filterNot(m => m.flags.is(Flags.FieldAccessor))
      .filter(_.paramSymss.isEmpty)
      .find(m => descriptionOf(m).isDefined)
      .foreach { m =>
        fail(
          s"method '${m.name}' has a @description annotation but no parameter list; parameterless members are treated as " +
            "properties, not tools - add an empty parameter list () to expose it as a tool"
        )
      }

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

      val optionClass = Symbol.requiredClass("scala.Option")
      val optionalFlags: List[Boolean] = paramTypes.map { tpe =>
        tpe.dealias match {
          case AppliedType(base, _) => base.typeSymbol == optionClass
          case _                    => false
        }
      }

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
      val optionalsExpr = Expr(optionalFlags)

      // The root schema must be named: TapirSchemaToJsonSchema only collects nested named schemas (e.g. case-class
      // parameters) into $defs when the root itself has a name - an unnamed root leaves dangling $refs that providers
      // reject.
      val schemaExpr: Expr[TapirSchema[Tup]] =
        '{ TapirSchema(SProduct[Tup]($fieldsExpr), name = Some(TapirSchema.SName(${ Expr(m.name) } + "Input"))) }

      val codecExpr: Expr[Codec[Tup]] = '{
        new Codec[Tup] {
          private val names = $namesExpr.toIndexedSeq
          private val decoders = $decodersExpr.toIndexedSeq
          private val encoders = $encodersExpr.toIndexedSeq
          private val optionals = $optionalsExpr.toIndexedSeq

          override def apply(c: io.circe.HCursor): Decoder.Result[Tup] =
            if (!c.value.isObject) Left(DecodingFailure("Tool arguments must be a JSON object", c.history))
            else {
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
            // Only Option-typed fields drop an encoded Json.Null (absent = None); a null produced by any other
            // field's encoder is real data and must survive the round-trip.
            val fields = names.iterator
              .zip(t.asInstanceOf[Product].productIterator)
              .zip(encoders.iterator)
              .zipWithIndex
              .flatMap { case (((name, value), enc), i) =>
                val encoded = enc.asInstanceOf[Encoder[Any]](value)
                if (encoded.isNull && optionals(i)) None else Some(name -> encoded)
              }
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
        case other                   => fail(s"method '${m.name}' has an unsupported shape: ${renderType(other)}")
      }

      params.zip(paramTypes).foreach { case (p, tpe) =>
        tpe match {
          case ByNameType(_) => fail(s"parameter '${p.name}' of method '${m.name}' is by-name, which is not supported")
          case _ if tpe.typeSymbol == defn.RepeatedParamClass =>
            fail(s"parameter '${p.name}' of method '${m.name}' is a vararg, which is not supported")
          case _ => ()
        }
      }

      // <:< (not =:=) so covariant overrides conform, e.g. an impl narrowing Option[String] to Some[String].
      if (!(resultType <:< TypeRepr.of[F[String]]))
        fail(s"method '${m.name}' must return ${renderType(TypeRepr.of[F[String]])}, but returns ${renderType(resultType)}")

      val tupleTpe = paramTypes.foldRight(TypeRepr.of[EmptyTuple])((t, acc) => TypeRepr.of[*:].appliedTo(List(t, acc)))
      tupleTpe.asType match {
        case '[tup] => buildTool[tup](m, toolDescription, params, paramTypes)
      }
    }

    Expr.ofList(toolExprs)
  }
}
