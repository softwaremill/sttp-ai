package sttp.ai.core.agent.testing

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.agent.{AgentTool, ConversationEntry, FinishReason, ResponseSchema}
import sttp.client4.testing.{BackendStub, SyncBackendStub}
import sttp.monad.MonadError
import sttp.tapir.Schema

class ScriptedAgentSpec extends AnyFlatSpec with Matchers with OptionValues {

  private val httpBackend = SyncBackendStub

  case class CalculatorInput(a: Double, b: Double)
  implicit val calculatorCodec: Codec[CalculatorInput] = deriveCodec
  implicit val calculatorSchema: Schema[CalculatorInput] = Schema.derived

  case class Answer(value: Int)
  implicit val answerCodec: Codec[Answer] = deriveCodec
  implicit val answerSchema: Schema[Answer] = Schema.derived

  private val calculatorTool = AgentTool.fromFunction("calculator", "Adds two numbers") { (input: CalculatorInput) =>
    s"Result: ${input.a + input.b}"
  }

  private def calculatorScript(): ScriptedAgent[sttp.shared.Identity] = ScriptedAgent.synchronous(
    ScriptedResponse.toolCall("calculator", """{"a": 1, "b": 2}"""),
    ScriptedResponse.text("The answer is 3")
  )

  "ScriptedAgent" should "drive the agent loop through a tool call to a final answer" in {
    val script = calculatorScript()
    val agent = script.builder.tools(calculatorTool).build

    val result = agent.run("What is 1 + 2?")(httpBackend)

    result.finalAnswer shouldBe "The answer is 3"
    result.finishReason shouldBe FinishReason.NaturalStop
    result.iterations shouldBe 2
    result.toolCalls should have size 1
    result.toolCalls.head.toolName shouldBe "calculator"
    result.toolCalls.head.output shouldBe "Result: 3.0"
  }

  it should "record prompts, offered tools and tool results across the run" in {
    val script = calculatorScript()
    script.builder.tools(calculatorTool).build.run("What is 1 + 2?")(httpBackend)

    script.requests should have size 2
    script.initialPrompt.value shouldBe "What is 1 + 2?"
    script.userPrompts should contain("What is 1 + 2?")
    script.offeredTools.map(_.name) shouldBe Seq("calculator")
    script.offeredTools.head.schema shouldBe calculatorTool.rawJsonSchema
    script.toolResultsSent shouldBe Seq(("calculator", "Result: 3.0"))
    script.systemPromptSent should not be empty // the default AgentConfig system prompt
  }

  it should "record includeTools = false on the last allowed iteration" in {
    val script = ScriptedAgent.synchronous(
      ScriptedResponse.toolCall("calculator", """{"a": 1, "b": 2}"""),
      ScriptedResponse.text("partial answer")
    )
    val result = script.builder.tools(calculatorTool).maxIterations(2).build.run("add 1 and 2")(httpBackend)

    script.requests.map(_.includeTools) shouldBe Seq(true, false)
    result.finalAnswer shouldBe "partial answer"
    result.finishReason shouldBe FinishReason.MaxIterations
  }

  it should "fail with ScriptExhaustedException when the loop outruns the script" in {
    val script = ScriptedAgent.synchronous(
      ScriptedResponse.toolCall("calculator", """{"a": 1, "b": 2}""")
    )
    val agent = script.builder.tools(calculatorTool).build

    intercept[ScriptExhaustedException](agent.run("add 1 and 2")(httpBackend))
  }

  it should "support runAs with a decoded final answer" in {
    val script = ScriptedAgent.synchronous(ScriptedResponse.text("""{"value": 3}"""))

    val result = script.builder.build.runAs[Answer]("compute")(httpBackend)

    result.finalAnswer shouldBe Right(Answer(3))
  }

  it should "record the configured response schema" in {
    val script = ScriptedAgent.synchronous(ScriptedResponse.text("""{"value": 3}"""))

    script.builder.deriveResponseSchema[Answer].build.runAs[Answer]("compute")(httpBackend)

    script.responseSchemaSent should not be empty
    script.responseSchemaSent.value.schema shouldBe ResponseSchema.derived[Answer]().schema
  }

  it should "record no response schema when none is configured" in {
    val script = calculatorScript()
    script.builder.tools(calculatorTool).build.run("What is 1 + 2?")(httpBackend)

    script.responseSchemaSent shouldBe empty
  }

  it should "give each build a fresh script cursor and accumulate recordings" in {
    val script = ScriptedAgent.synchronous(ScriptedResponse.text("hi"))

    script.builder.build.run("first")(httpBackend).finalAnswer shouldBe "hi"
    script.builder.build.run("second")(httpBackend).finalAnswer shouldBe "hi"

    script.requests should have size 2
  }

  it should "stop with TokenLimit when the script returns a max-tokens response" in {
    val script = ScriptedAgent.synchronous(ScriptedResponse.maxTokens("truncated answer"))

    val result = script.builder.build.run("go")(httpBackend)

    result.finalAnswer shouldBe "truncated answer"
    result.finishReason shouldBe FinishReason.TokenLimit
  }

  it should "execute tools from a text-with-tool-calls response and record the assistant text" in {
    val script = ScriptedAgent.synchronous(
      ScriptedResponse.textWithToolCalls("thinking...", "calculator" -> """{"a": 1, "b": 2}"""),
      ScriptedResponse.text("done")
    )

    val result = script.builder.tools(calculatorTool).build.run("add 1 and 2")(httpBackend)

    result.toolCalls.map(_.output) shouldBe Seq("Result: 3.0")
    result.finalAnswer shouldBe "done"
    script.requests.last.history.entries.collect { case ConversationEntry.AssistantResponse(content, _) =>
      content
    } should contain("thinking...")
  }

  it should "feed a tool-not-found result back for scripted calls to unregistered tools" in {
    val script = ScriptedAgent.synchronous(
      ScriptedResponse.toolCall("missing", "{}"),
      ScriptedResponse.text("done")
    )

    val result = script.builder.tools(calculatorTool).build.run("go")(httpBackend)

    result.toolCalls.map(_.output) shouldBe Seq("Tool not found: missing")
    script.toolResultsSent shouldBe Seq(("missing", "Tool not found: missing"))
  }

  private type Thunk[A] = () => A

  /** A minimal lazy effect: nothing runs until the thunk is applied. (Try/Future are eager, so they can't verify suspension.) */
  private implicit object ThunkMonad extends MonadError[Thunk] {
    override def unit[T](t: T): Thunk[T] = () => t
    override def map[T, T2](fa: Thunk[T])(f: T => T2): Thunk[T2] = () => f(fa())
    override def flatMap[T, T2](fa: Thunk[T])(f: T => Thunk[T2]): Thunk[T2] = () => f(fa())()
    override def error[T](t: Throwable): Thunk[T] = () => throw t
    override protected def handleWrappedError[T](rt: Thunk[T])(h: PartialFunction[Throwable, Thunk[T]]): Thunk[T] = () =>
      try rt()
      catch { case e: Throwable if h.isDefinedAt(e) => h(e)() }
    override def ensure[T](f: Thunk[T], e: => Thunk[Unit]): Thunk[T] = () =>
      try f()
      finally e()
  }

  it should "not consume the script until the effect runs" in {
    val script = ScriptedAgent[Thunk](ScriptedResponse.text("hi"))

    val effect = script.builder.build.run("prompt")(BackendStub[Thunk](ThunkMonad))
    script.requests shouldBe empty

    effect().finalAnswer shouldBe "hi"
    script.requests should have size 1
  }
}
