package sttp.ai.core.agent

import scala.quoted.*

/** Shared helpers for the agent layer's macros. */
private[agent] object MacroSupport {

  /** Renders a type from bare symbol names (recursing into type arguments and union branches) rather than via `tpe.show`. When a macro is
    * exercised via scala.compiletime.testing.typeCheckErrors (see AgentToolsDeriveErrorsSpec / UnionResponseSchemaSpec), types can be
    * declared inside the typechecked snippet; `.show`ing such a type after other symbol lookups (methodMembers, getAnnotation, ...) have
    * run triggers a dotty CyclicReference in that harness. Recursing through symbol names avoids forcing that printing while still
    * preserving type arguments (e.g. `List[Foo]`, not just `List`).
    */
  def renderType(using q: Quotes)(t: q.reflect.TypeRepr): String = {
    import q.reflect.*
    t.dealias match {
      case AppliedType(base, args) => s"${base.typeSymbol.name}[${args.map(a => renderType(a)).mkString(", ")}]"
      case OrType(left, right)     => s"${renderType(left)} | ${renderType(right)}"
      case other                   => other.typeSymbol.name
    }
  }
}
