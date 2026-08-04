package sttp.ai.core.agent.testing

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.scalatest.exceptions.TestFailedException
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.agent.{AgentResult, AgentTool, FinishReason}
import sttp.client4.testing.SyncBackendStub
import sttp.shared.Identity
import sttp.tapir.Schema

class AgentMatchersSpec extends AnyFlatSpec with Matchers with AgentMatchers {

  case class CalculatorInput(a: Double, b: Double)
  implicit val calculatorCodec: Codec[CalculatorInput] = deriveCodec
  implicit val calculatorSchema: Schema[CalculatorInput] = Schema.derived

  private val calculatorTool = AgentTool.fromFunction("calculator", "Adds two numbers") { (input: CalculatorInput) =>
    s"Result: ${input.a + input.b}"
  }

  private val (script, result): (ScriptedAgent[Identity], AgentResult[String]) = {
    val s = ScriptedAgent.synchronous(
      ScriptedResponse.toolCall("calculator", """{"a": 1, "b": 2}"""),
      ScriptedResponse.text("The answer is 3")
    )
    val r = s.builder.tools(calculatorTool).build.run("What is 1 + 2?")(SyncBackendStub)
    (s, r)
  }

  "haveReceivedPrompt" should "pass for a prompt that was sent" in {
    script should haveReceivedPrompt("What is 1 + 2?")
  }

  it should "fail listing the prompts that were sent" in {
    val e = intercept[TestFailedException](script should haveReceivedPrompt("something else"))
    e.getMessage should include("something else")
    e.getMessage should include("What is 1 + 2?")
  }

  "haveOfferedTool" should "pass for an offered tool" in {
    script should haveOfferedTool("calculator")
  }

  it should "fail listing the tools that were offered" in {
    val e = intercept[TestFailedException](script should haveOfferedTool("search"))
    e.getMessage should include("search")
    e.getMessage should include("calculator")
  }

  "haveOfferedToolWithSchema" should "pass when the schema matches structurally" in {
    script should haveOfferedToolWithSchema("calculator", calculatorTool.rawJsonSchema)
  }

  it should "fail showing both schemas when they differ" in {
    val e = intercept[TestFailedException](script should haveOfferedToolWithSchema("calculator", io.circe.Json.obj()))
    e.getMessage should include(calculatorTool.rawJsonSchema.noSpaces) // the actual schema
    e.getMessage should include("expected {}") // the expected schema
  }

  it should "point at last-iteration tool withholding when no tools were offered at all" in {
    val script = ScriptedAgent.synchronous(ScriptedResponse.text("done"))
    script.builder.tools(calculatorTool).maxIterations(1).build.run("go")(SyncBackendStub)

    val e = intercept[TestFailedException](script should haveOfferedTool("calculator"))
    e.getMessage should include("withholds tools on the last allowed iteration")
  }

  "haveCalledTool" should "pass for a called tool" in {
    result should haveCalledTool("calculator")
  }

  it should "fail listing the tools that were called" in {
    val e = intercept[TestFailedException](result should haveCalledTool("search"))
    e.getMessage should include("calculator")
  }

  "haveCalledToolWith" should "compare arguments as JSON, insensitive to field order and whitespace" in {
    result should haveCalledToolWith("calculator", """{ "b": 2, "a": 1 }""")
  }

  it should "fail showing the recorded arguments" in {
    val e = intercept[TestFailedException](result should haveCalledToolWith("calculator", """{"a": 9, "b": 9}"""))
    e.getMessage should include("""{"a": 1, "b": 2}""")
  }

  it should "fail cleanly when the expected arguments are not valid JSON" in {
    val e = intercept[TestFailedException](result should haveCalledToolWith("calculator", "not json"))
    e.getMessage should include("not valid JSON")
  }

  "haveFinishedWith" should "pass for the actual finish reason" in {
    result should haveFinishedWith(FinishReason.NaturalStop)
  }

  it should "pass for a token-limit stop" in {
    val script = ScriptedAgent.synchronous(ScriptedResponse.maxTokens("truncated"))
    val tokenLimited = script.builder.build.run("go")(SyncBackendStub)

    tokenLimited should haveFinishedWith(FinishReason.TokenLimit)
  }

  it should "fail showing the actual finish reason" in {
    val e = intercept[TestFailedException](result should haveFinishedWith(FinishReason.TokenLimit))
    e.getMessage should include("NaturalStop")
  }

  "AgentMatchers" should "also be usable via the companion object" in {
    script should AgentMatchers.haveOfferedTool("calculator")
  }
}
