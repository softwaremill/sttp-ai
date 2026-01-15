package sttp.ai.core.agent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.json.SnakePickle
import sttp.client4.testing.SyncBackendStub
import sttp.monad.IdentityMonad
import sttp.shared.Identity
import sttp.tapir.Schema

class AgentSpec extends AnyFlatSpec with Matchers {

  class StubAgentBackend(responses: Seq[AgentResponse]) extends AgentBackend[Identity] {
    private var callCount = 0
    var receivedHistories: Seq[ConversationHistory] = Seq.empty

    override def tools: Seq[AgentTool[_]] = Seq.empty
    override def systemPrompt: Option[String] = None

    override def sendRequest(
        history: ConversationHistory,
        backend: sttp.client4.Backend[Identity]
    ): Identity[AgentResponse] = {
      receivedHistories = receivedHistories :+ history
      val response = if (callCount < responses.length) {
        responses(callCount)
      } else {
        responses.last
      }
      callCount += 1
      response
    }
  }

  private val backend = SyncBackendStub

  case class CalculatorInput(a: Double, b: Double)
  implicit val calculatorInputRW: SnakePickle.ReadWriter[CalculatorInput] = SnakePickle.macroRW
  implicit val calculatorInputSchema: Schema[CalculatorInput] = Schema.derived

  private val calculatorTool = AgentTool.fromFunction(
    "calculator",
    "Calculate"
  ) { (input: CalculatorInput) =>
    s"Result: ${input.a + input.b}"
  }

  private def createLoop(responses: Seq[AgentResponse], config: AgentConfig): (Agent[Identity], StubAgentBackend) = {
    val stubBackend = new StubAgentBackend(responses)
    val loop = new Agent[Identity](stubBackend, config)(IdentityMonad)
    (loop, stubBackend)
  }

  private def runLoop(responses: Seq[AgentResponse], tools: Seq[AgentTool[_]] = Seq.empty): AgentResult[String] = {
    val testConfig = AgentConfig(maxIterations = 5, userTools = tools).right.get
    val (loop, _) = createLoop(responses, testConfig)
    loop.run("Test")(backend)
  }

  case class DummyInput()
  implicit val dummyInputRW: SnakePickle.ReadWriter[DummyInput] = SnakePickle.macroRW
  implicit val dummyInputSchema: Schema[DummyInput] = Schema.derived

  case class NumberInput(value: Int)
  implicit val numberInputRW: SnakePickle.ReadWriter[NumberInput] = SnakePickle.macroRW
  implicit val numberInputSchema: Schema[NumberInput] = Schema.derived

  "Agent" should "stop after max iterations" in {
    val dummyTool = AgentTool.fromFunction(
      "dummy",
      "Dummy tool"
    )((_: DummyInput) => "dummy result")

    val result = runLoop(
      Seq(
        AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "dummy", input = "{}")), StopReason.ToolUse),
        AgentResponse("", Seq(ToolCall(id = "call_2", toolName = "dummy", input = "{}")), StopReason.ToolUse),
        AgentResponse("", Seq(ToolCall(id = "call_3", toolName = "dummy", input = "{}")), StopReason.ToolUse),
        AgentResponse("", Seq(ToolCall(id = "call_4", toolName = "dummy", input = "{}")), StopReason.ToolUse),
        AgentResponse("", Seq(ToolCall(id = "call_5", toolName = "dummy", input = "{}")), StopReason.ToolUse)
      ),
      tools = Seq(dummyTool)
    )

    result.finishReason shouldBe FinishReason.MaxIterations
    result.iterations shouldBe 5
    result.toolCalls should have size 5
  }

  it should "stop when finish tool is called" in {
    val result = runLoop(
      Seq(
        AgentResponse(
          "",
          Seq(
            ToolCall(id = "call_1", toolName = "finish", input = """{"answer":"Final answer"}""")
          ),
          StopReason.ToolUse
        )
      )
    )

    result.finishReason shouldBe FinishReason.ToolFinish
    result.finalAnswer shouldBe "Final answer"
    result.iterations shouldBe 1
    result.toolCalls should have size 1
  }

  it should "stop on natural stop when no tool calls" in {
    val result = runLoop(
      Seq(
        AgentResponse("Final answer without tools", Seq.empty, StopReason.EndTurn)
      )
    )

    result.finishReason shouldBe FinishReason.NaturalStop
    result.finalAnswer shouldBe "Final answer without tools"
    result.iterations shouldBe 1
    result.toolCalls shouldBe empty
  }

  it should "execute multiple tool calls in one iteration" in {
    val result = runLoop(
      Seq(
        AgentResponse(
          "",
          Seq(
            ToolCall(id = "call_1", toolName = "calculator", input = """{"a":5,"b":10}"""),
            ToolCall(id = "call_2", toolName = "calculator", input = """{"a":3,"b":7}""")
          ),
          StopReason.ToolUse
        ),
        AgentResponse("Done", Seq.empty, StopReason.EndTurn)
      ),
      tools = Seq(calculatorTool)
    )

    result.iterations shouldBe 2
    result.toolCalls should have size 2
    result.toolCalls(0).toolName shouldBe "calculator"
    result.toolCalls(0).output shouldBe "Result: 15.0"
    result.toolCalls(0).iteration shouldBe 1
    result.toolCalls(1).toolName shouldBe "calculator"
    result.toolCalls(1).output shouldBe "Result: 10.0"
    result.toolCalls(1).iteration shouldBe 1
  }

  it should "handle tool execution errors gracefully" in {
    val errorTool = AgentTool.fromFunction(
      "error_tool",
      "Tool that throws"
    ) { (_: DummyInput) =>
      throw new RuntimeException("Tool error")
    }

    val result = runLoop(
      Seq(
        AgentResponse(
          "",
          Seq(
            ToolCall(id = "call_1", toolName = "error_tool", input = "{}")
          ),
          StopReason.ToolUse
        ),
        AgentResponse("Recovered", Seq.empty, StopReason.EndTurn)
      ),
      tools = Seq(errorTool)
    )

    result.iterations shouldBe 2
    result.toolCalls should have size 1
    result.toolCalls.head.toolName shouldBe "error_tool"
    result.toolCalls.head.output should include("Error executing tool")
    result.toolCalls.head.output should include("Tool error")
  }

  it should "handle unknown tool gracefully" in {
    val result = runLoop(
      Seq(
        AgentResponse(
          "",
          Seq(
            ToolCall(id = "call_1", toolName = "unknown_tool", input = "{}")
          ),
          StopReason.ToolUse
        ),
        AgentResponse("Recovered", Seq.empty, StopReason.EndTurn)
      )
    )

    result.iterations shouldBe 2
    result.toolCalls should have size 1
    result.toolCalls.head.toolName shouldBe "unknown_tool"
    result.toolCalls.head.output shouldBe "Tool not found: unknown_tool"
  }

  it should "build tool call records correctly" in {
    val result = runLoop(
      Seq(
        AgentResponse(
          "",
          Seq(
            ToolCall(id = "call_1", toolName = "calculator", input = """{"a":5,"b":10}""")
          ),
          StopReason.ToolUse
        ),
        AgentResponse("Done", Seq.empty, StopReason.EndTurn)
      ),
      tools = Seq(calculatorTool)
    )

    result.toolCalls should have size 1
    val record = result.toolCalls.head
    record.toolName shouldBe "calculator"
    record.input should include("\"a\"")
    record.input should include("5")
    record.input should include("\"b\"")
    record.input should include("10")
    record.output shouldBe "Result: 15.0"
    record.iteration shouldBe 1
  }

  it should "add iteration markers after first iteration" in {
    val config = AgentConfig(maxIterations = 3).right.get
    val (loop, stubBackend) = createLoop(
      Seq(
        AgentResponse("First", Seq.empty, StopReason.EndTurn),
        AgentResponse("Second", Seq.empty, StopReason.EndTurn)
      ),
      config
    )

    loop.run("Test")(backend)

    stubBackend.receivedHistories should have size 1
    val firstHistory = stubBackend.receivedHistories.head
    firstHistory.entries.exists(_.isInstanceOf[ConversationEntry.IterationMarker]) shouldBe false
  }

  it should "finish tool has priority over other tools" in {
    val result = runLoop(
      Seq(
        AgentResponse(
          "",
          Seq(
            ToolCall(id = "call_1", toolName = "calculator", input = """{"a":5,"b":10}"""),
            ToolCall(id = "call_2", toolName = "finish", input = """{"answer":"Early finish"}""")
          ),
          StopReason.ToolUse
        )
      ),
      tools = Seq(calculatorTool)
    )

    result.finishReason shouldBe FinishReason.ToolFinish
    result.finalAnswer shouldBe "Early finish"
    result.iterations shouldBe 1
    result.toolCalls should have size 2
    result.toolCalls(0).toolName shouldBe "calculator"
    result.toolCalls(1).toolName shouldBe "finish"
  }

  it should "reject user tools with reserved names" in {
    val userFinishTool = AgentTool.fromFunction(
      "finish",
      "User finish tool"
    )((_: DummyInput) => "custom finish")

    val configResult = AgentConfig(userTools = Seq(userFinishTool))

    configResult shouldBe a[Left[_, _]]
    configResult.left.toOption.get should include("reserved names")
    configResult.left.toOption.get should include("finish")
    configResult.left.toOption.get should include("system tools")
  }

  it should "accept valid user tools" in {
    val customTool = AgentTool.fromFunction(
      "custom",
      "Custom tool"
    )((_: DummyInput) => "result")

    val configResult = AgentConfig(userTools = Seq(customTool))

    configResult shouldBe a[Right[_, _]]
    val config = configResult.toOption.get
    config.userTools should have size 1
    config.userTools.head.name shouldBe "custom"
  }

  it should "extract final answer from last tool result on max iterations" in {
    val dummyTool = AgentTool.fromFunction(
      "dummy",
      "Dummy tool"
    )((_: DummyInput) => "final tool result")

    val config = AgentConfig(maxIterations = 3, userTools = Seq(dummyTool)).right.get
    val (loop, _) = createLoop(
      Seq(
        AgentResponse("First response", Seq(ToolCall(id = "call_1", toolName = "dummy", input = "{}")), StopReason.ToolUse),
        AgentResponse("Second response", Seq(ToolCall(id = "call_2", toolName = "dummy", input = "{}")), StopReason.ToolUse),
        AgentResponse("Third response", Seq(ToolCall(id = "call_3", toolName = "dummy", input = "{}")), StopReason.ToolUse)
      ),
      config
    )

    val result = loop.run("Test")(backend)

    result.finishReason shouldBe FinishReason.MaxIterations
    result.finalAnswer shouldBe "final tool result"
    result.iterations shouldBe 3
  }

  "Agent with default ExceptionHandler" should "propagate IOException from tool" in {
    val ioTool = AgentTool.fromFunction(
      "io_tool",
      "IO tool"
    ) { (_: DummyInput) =>
      throw new java.io.IOException("Network error")
    }

    val config = AgentConfig(
      maxIterations = 3,
      userTools = Seq(ioTool),
      exceptionHandler = ExceptionHandler.default
    ).toOption.get

    val (loop, _) = createLoop(
      Seq(AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "io_tool", input = "{}")), StopReason.ToolUse)),
      config
    )

    assertThrows[java.io.IOException] {
      loop.run("Test")(backend)
    }
  }

  it should "propagate InterruptedException from tool" in {
    val interruptedTool = AgentTool.fromFunction(
      "interrupted_tool",
      "Interrupted tool"
    ) { (_: DummyInput) =>
      throw new InterruptedException("Thread interrupted")
    }

    val config = AgentConfig(
      maxIterations = 3,
      userTools = Seq(interruptedTool),
      exceptionHandler = ExceptionHandler.default
    ).toOption.get

    val (loop, _) = createLoop(
      Seq(AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "interrupted_tool", input = "{}")), StopReason.ToolUse)),
      config
    )

    assertThrows[InterruptedException] {
      loop.run("Test")(backend)
    }
  }

  it should "send RuntimeException to LLM and continue loop" in {
    val errorTool = AgentTool.fromFunction(
      "error_tool",
      "Error tool"
    ) { (_: DummyInput) =>
      throw new RuntimeException("Logic error")
    }

    val config = AgentConfig(
      maxIterations = 3,
      userTools = Seq(errorTool),
      exceptionHandler = ExceptionHandler.default
    ).toOption.get

    val (loop, _) = createLoop(
      Seq(
        AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "error_tool", input = "{}")), StopReason.ToolUse),
        AgentResponse("Recovered", Seq.empty, StopReason.EndTurn)
      ),
      config
    )

    val result = loop.run("Test")(backend)

    result.toolCalls should have size 1
    result.toolCalls.head.output should include("Error executing tool 'error_tool'")
    result.toolCalls.head.output should include("Logic error")
    result.finishReason shouldBe FinishReason.NaturalStop
  }

  "Agent with sendAllToLLM ExceptionHandler" should "send IOException to LLM instead of propagating" in {
    val ioTool = AgentTool.fromFunction(
      "io_tool",
      "IO tool"
    ) { (_: DummyInput) =>
      throw new java.io.IOException("Network error")
    }

    val config = AgentConfig(
      maxIterations = 3,
      userTools = Seq(ioTool),
      exceptionHandler = ExceptionHandler.sendAllToLLM
    ).toOption.get

    val (loop, _) = createLoop(
      Seq(
        AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "io_tool", input = "{}")), StopReason.ToolUse),
        AgentResponse("Recovered", Seq.empty, StopReason.EndTurn)
      ),
      config
    )

    val result = loop.run("Test")(backend)

    result.toolCalls.head.output should include("Error executing tool 'io_tool'")
    result.toolCalls.head.output should include("Network error")
    result.finishReason shouldBe FinishReason.NaturalStop
  }

  "Agent with propagateAll ExceptionHandler" should "propagate all errors" in {
    val errorTool = AgentTool.fromFunction(
      "error_tool",
      "Error tool"
    ) { (_: DummyInput) =>
      throw new RuntimeException("Any error")
    }

    val config = AgentConfig(
      maxIterations = 3,
      userTools = Seq(errorTool),
      exceptionHandler = ExceptionHandler.propagateAll
    ).toOption.get

    val (loop, _) = createLoop(
      Seq(AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "error_tool", input = "{}")), StopReason.ToolUse)),
      config
    )

    assertThrows[RuntimeException] {
      loop.run("Test")(backend)
    }
  }

  "Agent with parse errors" should "send descriptive error to LLM" in {
    val numberTool = AgentTool.fromFunction(
      "number",
      "Number tool"
    ) { (input: NumberInput) =>
      s"Got: ${input.value}"
    }

    val config = AgentConfig(
      maxIterations = 3,
      userTools = Seq(numberTool)
    ).toOption.get

    val (loop, _) = createLoop(
      Seq(
        // Invalid input - string instead of int
        AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "number", input = """{"value":"not_a_number"}""")), StopReason.ToolUse),
        AgentResponse("Fixed", Seq.empty, StopReason.EndTurn)
      ),
      config
    )

    val result = loop.run("Test")(backend)

    result.toolCalls.head.output should include("Invalid arguments for tool 'number'")
    result.toolCalls.head.output should include("Please check the tool definition")
    result.finishReason shouldBe FinishReason.NaturalStop
  }

  it should "handle malformed JSON" in {
    val config = AgentConfig(
      maxIterations = 3,
      userTools = Seq(calculatorTool)
    ).toOption.get

    val (loop, _) = createLoop(
      Seq(
        AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "calculator", input = """{"a":not valid json}""")), StopReason.ToolUse),
        AgentResponse("Fixed", Seq.empty, StopReason.EndTurn)
      ),
      config
    )

    val result = loop.run("Test")(backend)

    result.toolCalls.head.output should include("Invalid arguments")
    result.finishReason shouldBe FinishReason.NaturalStop
  }

  "Agent with custom ExceptionHandler" should "use custom error formatting" in {
    val customHandler = new ExceptionHandler {
      def handleToolException(toolName: String, exception: Exception): Either[String, Exception] =
        Left(s"CUSTOM ERROR in $toolName: ${exception.getClass.getSimpleName}")

      def handleParseError(
          toolName: String,
          rawArguments: String,
          parseException: Exception
      ): Either[String, Exception] =
        Left(s"CUSTOM PARSE ERROR in $toolName")
    }

    val errorTool = AgentTool.fromFunction(
      "error_tool",
      "Error tool"
    ) { (_: DummyInput) =>
      throw new IllegalArgumentException("Bad argument")
    }

    val config = AgentConfig(
      maxIterations = 3,
      userTools = Seq(errorTool),
      exceptionHandler = customHandler
    ).toOption.get

    val (loop, _) = createLoop(
      Seq(
        AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "error_tool", input = "{}")), StopReason.ToolUse),
        AgentResponse("Done", Seq.empty, StopReason.EndTurn)
      ),
      config
    )

    val result = loop.run("Test")(backend)

    result.toolCalls.head.output shouldBe "CUSTOM ERROR in error_tool: IllegalArgumentException"
  }
}
