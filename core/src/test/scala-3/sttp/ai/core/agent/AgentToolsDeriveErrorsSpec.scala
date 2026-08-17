package sttp.ai.core.agent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.compiletime.testing.typeCheckErrors

class AgentToolsDeriveErrorsSpec extends AnyFlatSpec with Matchers {

  private def messagesOf(errors: List[scala.compiletime.testing.Error]): String =
    errors.map(_.message).mkString("\n")

  behavior of "AgentTools.derive compile-time validation"

  it should "reject a method without @description" in {
    val errors = typeCheckErrors("""
      trait NoDesc { def a(x: Int): String }
      sttp.ai.core.agent.AgentTools.derive[NoDesc](null.asInstanceOf[NoDesc])
    """)
    messagesOf(errors) should include("missing a @description")
  }

  it should "reject overloaded methods" in {
    val errors = typeCheckErrors("""
      trait Overloaded {
        @sttp.tapir.Schema.annotations.description("one") def a(x: Int): String
        @sttp.tapir.Schema.annotations.description("two") def a(x: String): String
      }
      sttp.ai.core.agent.AgentTools.derive[Overloaded](null.asInstanceOf[Overloaded])
    """)
    messagesOf(errors) should include("overloaded methods are not supported")
  }

  it should "reject a wrong return type" in {
    val errors = typeCheckErrors("""
      trait WrongReturn {
        @sttp.tapir.Schema.annotations.description("d") def a(x: Int): Int
      }
      sttp.ai.core.agent.AgentTools.derive[WrongReturn](null.asInstanceOf[WrongReturn])
    """)
    messagesOf(errors) should include("must return")
  }

  it should "reject default parameter values" in {
    val errors = typeCheckErrors("""
      trait Defaults {
        @sttp.tapir.Schema.annotations.description("d") def a(x: Int = 5): String
      }
      sttp.ai.core.agent.AgentTools.derive[Defaults](null.asInstanceOf[Defaults])
    """)
    messagesOf(errors) should include("default parameter values")
  }

  it should "reject multiple parameter lists" in {
    val errors = typeCheckErrors("""
      trait Curried {
        @sttp.tapir.Schema.annotations.description("d") def a(x: Int)(y: Int): String
      }
      sttp.ai.core.agent.AgentTools.derive[Curried](null.asInstanceOf[Curried])
    """)
    messagesOf(errors) should include("multiple parameter lists")
  }

  it should "reject using parameters" in {
    val errors = typeCheckErrors("""
      trait Using {
        @sttp.tapir.Schema.annotations.description("d") def a(x: Int)(using y: Ordering[Int]): String
      }
      sttp.ai.core.agent.AgentTools.derive[Using](null.asInstanceOf[Using])
    """)
    val messages = messagesOf(errors)
    (messages.contains("implicit/using parameters") || messages.contains("multiple parameter lists")) shouldBe true
  }

  it should "reject method type parameters" in {
    val errors = typeCheckErrors("""
      trait Poly {
        @sttp.tapir.Schema.annotations.description("d") def a[T](x: T): String
      }
      sttp.ai.core.agent.AgentTools.derive[Poly](null.asInstanceOf[Poly])
    """)
    messagesOf(errors) should include("type parameters")
  }

  it should "reject a parameter type without a given Schema" in {
    val errors = typeCheckErrors("""
      class Opaque(val x: Int)
      trait NoSchema {
        @sttp.tapir.Schema.annotations.description("d") def a(o: Opaque): String
      }
      sttp.ai.core.agent.AgentTools.derive[NoSchema](null.asInstanceOf[NoSchema])
    """)
    messagesOf(errors) should include("no given sttp.tapir.Schema")
  }

  it should "preserve type arguments in the error message for a generic parameter type without a given Schema" in {
    val errors = typeCheckErrors("""
      class Opaque(val x: Int)
      trait NoSchema {
        @sttp.tapir.Schema.annotations.description("d") def a(o: List[Opaque]): String
      }
      sttp.ai.core.agent.AgentTools.derive[NoSchema](null.asInstanceOf[NoSchema])
    """)
    messagesOf(errors) should include("List[")
  }
}
