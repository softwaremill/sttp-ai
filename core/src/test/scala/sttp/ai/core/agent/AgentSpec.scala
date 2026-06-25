package sttp.ai.core.agent

import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.decode
import sttp.client4.testing.SyncBackendStub
import sttp.monad.IdentityMonad
import sttp.shared.Identity
import sttp.tapir.Schema

class AgentSpec extends AnyFlatSpec with Matchers with OptionValues {

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
  implicit val calculatorInputCodec: Codec[CalculatorInput] = deriveCodec
  implicit val calculatorInputSchema: Schema[CalculatorInput] = Schema.derived

  private val calculatorTool = AgentTool.fromFunction(
    "calculator",
    "Calculate"
  ) { (input: CalculatorInput) =>
    s"Result: ${input.a + input.b}"
  }

  private def agentBuilder(responses: AgentResponse*): AgentBuilder[Identity] =
    AgentBuilder[Identity](_ => new StubAgentBackend(responses))(IdentityMonad)

  private def runLoop(builder: AgentBuilder[Identity]): AgentResult[String] =
    builder.build.run("Test")(backend)

  case class DummyInput()
  implicit val dummyInputCodec: Codec[DummyInput] = deriveCodec
  implicit val dummyInputSchema: Schema[DummyInput] = Schema.derived

  case class NumberInput(value: Int)
  implicit val numberInputCodec: Codec[NumberInput] = deriveCodec
  implicit val numberInputSchema: Schema[NumberInput] = Schema.derived

  "Agent" should "stop after max iterations" in {
    val dummyTool = AgentTool.fromFunction(
      "dummy",
      "Dummy tool"
    )((_: DummyInput) => "dummy result")

    val result = runLoop(
      agentBuilder(
        AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "dummy", input = "{}")), StopReason.ToolUse),
        AgentResponse("", Seq(ToolCall(id = "call_2", toolName = "dummy", input = "{}")), StopReason.ToolUse),
        AgentResponse("", Seq(ToolCall(id = "call_3", toolName = "dummy", input = "{}")), StopReason.ToolUse),
        AgentResponse("", Seq(ToolCall(id = "call_4", toolName = "dummy", input = "{}")), StopReason.ToolUse),
        AgentResponse("", Seq(ToolCall(id = "call_5", toolName = "dummy", input = "{}")), StopReason.ToolUse)
      ).maxIterations(5).tools(dummyTool)
    )

    result.finishReason shouldBe FinishReason.MaxIterations
    result.iterations shouldBe 5
    result.toolCalls should have size 5
  }

  it should "complete the loop when the response has no tool calls and empty text" in {
    val result = runLoop(agentBuilder(AgentResponse("", Seq.empty, StopReason.EndTurn)))

    result.finishReason shouldBe FinishReason.NaturalStop
    result.finalAnswer shouldBe ""
    result.iterations shouldBe 1
    result.toolCalls shouldBe empty
  }

  it should "execute multiple tool calls in one iteration" in {
    val result = runLoop(
      agentBuilder(
        AgentResponse(
          "",
          Seq(
            ToolCall(id = "call_1", toolName = "calculator", input = """{"a":5,"b":10}"""),
            ToolCall(id = "call_2", toolName = "calculator", input = """{"a":3,"b":7}""")
          ),
          StopReason.ToolUse
        ),
        AgentResponse("Done", Seq.empty, StopReason.EndTurn)
      ).tools(calculatorTool)
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
      agentBuilder(
        AgentResponse(
          "",
          Seq(
            ToolCall(id = "call_1", toolName = "error_tool", input = "{}")
          ),
          StopReason.ToolUse
        ),
        AgentResponse("Recovered", Seq.empty, StopReason.EndTurn)
      ).tools(errorTool)
    )

    result.iterations shouldBe 2
    result.toolCalls should have size 1
    result.toolCalls.head.toolName shouldBe "error_tool"
    result.toolCalls.head.output should include("Error executing tool")
    result.toolCalls.head.output should include("Tool error")
    result.finishReason shouldBe FinishReason.NaturalStop
  }

  it should "handle unknown tool gracefully" in {
    val result = runLoop(
      agentBuilder(
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
      agentBuilder(
        AgentResponse(
          "",
          Seq(
            ToolCall(id = "call_1", toolName = "calculator", input = """{"a":5,"b":10}""")
          ),
          StopReason.ToolUse
        ),
        AgentResponse("Done", Seq.empty, StopReason.EndTurn)
      ).tools(calculatorTool)
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
    val dummyTool = AgentTool.fromFunction(
      "dummy",
      "Dummy tool"
    )((_: DummyInput) => "dummy result")

    val stubBackend = new StubAgentBackend(
      Seq(
        AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "dummy", input = "{}")), StopReason.ToolUse),
        AgentResponse("Done", Seq.empty, StopReason.EndTurn)
      )
    )
    runLoop(AgentBuilder[Identity](_ => stubBackend)(IdentityMonad).tools(dummyTool))

    stubBackend.receivedHistories should have size 2
    val firstHistory = stubBackend.receivedHistories.head
    firstHistory.entries.exists(_.isInstanceOf[ConversationEntry.IterationMarker]) shouldBe false
    val secondHistory = stubBackend.receivedHistories(1)
    secondHistory.entries.exists(_.isInstanceOf[ConversationEntry.IterationMarker]) shouldBe true
  }

  it should "accept valid user tools" in {
    val customTool = AgentTool.fromFunction(
      "custom",
      "Custom tool"
    )((_: DummyInput) => "result")

    val config = agentBuilder().tools(customTool).config

    config.userTools should have size 1
    config.userTools.head.name shouldBe "custom"
  }

  it should "extract final answer from last tool result on max iterations" in {
    val dummyTool = AgentTool.fromFunction(
      "dummy",
      "Dummy tool"
    )((_: DummyInput) => "final tool result")

    val result = runLoop(
      agentBuilder(
        AgentResponse("First response", Seq(ToolCall(id = "call_1", toolName = "dummy", input = "{}")), StopReason.ToolUse),
        AgentResponse("Second response", Seq(ToolCall(id = "call_2", toolName = "dummy", input = "{}")), StopReason.ToolUse),
        AgentResponse("Third response", Seq(ToolCall(id = "call_3", toolName = "dummy", input = "{}")), StopReason.ToolUse)
      ).maxIterations(3).tools(dummyTool)
    )

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

    assertThrows[java.io.IOException] {
      runLoop(
        agentBuilder(
          AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "io_tool", input = "{}")), StopReason.ToolUse)
        ).tools(ioTool).exceptionHandler(ExceptionHandler.default)
      )
    }
  }

  it should "propagate InterruptedException from tool" in {
    val interruptedTool = AgentTool.fromFunction(
      "interrupted_tool",
      "Interrupted tool"
    ) { (_: DummyInput) =>
      throw new InterruptedException("Thread interrupted")
    }

    assertThrows[InterruptedException] {
      runLoop(
        agentBuilder(
          AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "interrupted_tool", input = "{}")), StopReason.ToolUse)
        ).tools(interruptedTool).exceptionHandler(ExceptionHandler.default)
      )
    }
  }

  "Agent with sendAllToLLM ExceptionHandler" should "send IOException to LLM instead of propagating" in {
    val ioTool = AgentTool.fromFunction(
      "io_tool",
      "IO tool"
    ) { (_: DummyInput) =>
      throw new java.io.IOException("Network error")
    }

    val result = runLoop(
      agentBuilder(
        AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "io_tool", input = "{}")), StopReason.ToolUse),
        AgentResponse("Recovered", Seq.empty, StopReason.EndTurn)
      ).tools(ioTool).exceptionHandler(ExceptionHandler.sendAllToLLM)
    )

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

    assertThrows[RuntimeException] {
      runLoop(
        agentBuilder(
          AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "error_tool", input = "{}")), StopReason.ToolUse)
        ).tools(errorTool).exceptionHandler(ExceptionHandler.propagateAll)
      )
    }
  }

  "Agent with parse errors" should "send descriptive error to LLM" in {
    val numberTool = AgentTool.fromFunction(
      "number",
      "Number tool"
    ) { (input: NumberInput) =>
      s"Got: ${input.value}"
    }

    val result = runLoop(
      agentBuilder(
        // Invalid input - string instead of int
        AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "number", input = """{"value":"not_a_number"}""")), StopReason.ToolUse),
        AgentResponse("Fixed", Seq.empty, StopReason.EndTurn)
      ).tools(numberTool)
    )

    result.toolCalls.head.output should include("Invalid arguments for tool 'number'")
    result.toolCalls.head.output should include("Please check the tool definition")
    result.finishReason shouldBe FinishReason.NaturalStop
  }

  it should "handle malformed JSON" in {
    val result = runLoop(
      agentBuilder(
        AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "calculator", input = """{"a":not valid json}""")), StopReason.ToolUse),
        AgentResponse("Fixed", Seq.empty, StopReason.EndTurn)
      ).tools(calculatorTool)
    )

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

    val result = runLoop(
      agentBuilder(
        AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "error_tool", input = "{}")), StopReason.ToolUse),
        AgentResponse("Done", Seq.empty, StopReason.EndTurn)
      ).tools(errorTool).exceptionHandler(customHandler)
    )

    result.toolCalls.head.output shouldBe "CUSTOM ERROR in error_tool: IllegalArgumentException"
  }

  case class WeatherSummary(city: String, tempC: Double, conditions: String)
  object WeatherSummary {
    implicit val codec: Codec[WeatherSummary] =
      Codec.forProduct3("city", "temp_c", "conditions")(WeatherSummary.apply)(w => (w.city, w.tempC, w.conditions))
    implicit val schema: Schema[WeatherSummary] = Schema.derived
  }

  "Agent with responseSchema" should "round-trip the typed payload through the final answer" in {
    val result = agentBuilder(
      AgentResponse("""{"city":"Krakow","temp_c":12.0,"conditions":"sunny"}""", Seq.empty, StopReason.EndTurn)
    ).deriveResponseSchema[WeatherSummary].build.run("What's the weather?")(backend)

    result.finishReason shouldBe FinishReason.NaturalStop: Unit
    decode[WeatherSummary](result.finalAnswer) shouldBe Right(WeatherSummary("Krakow", 12.0, "sunny"))
  }

  "Agent.runAs[T]" should "return Right(T) when the model emits a well-formed structured payload" in {
    val result = agentBuilder(
      AgentResponse("""{"city":"Krakow","temp_c":12.0,"conditions":"sunny"}""", Seq.empty, StopReason.EndTurn)
    ).deriveResponseSchema[WeatherSummary].build.runAs[WeatherSummary]("What's the weather?")(backend)

    result.finishReason shouldBe FinishReason.NaturalStop: Unit
    result.iterations shouldBe 1: Unit
    result.finalAnswer shouldBe Right(WeatherSummary("Krakow", 12.0, "sunny"))
  }

  it should "return Left(AgentParseError) preserving the trace when the answer can't be parsed as T" in {
    val result = agentBuilder(
      AgentResponse("""{"wrong":"shape"}""", Seq.empty, StopReason.EndTurn)
    ).deriveResponseSchema[WeatherSummary].build.runAs[WeatherSummary]("What's the weather?")(backend)

    result.iterations shouldBe 1: Unit
    result.finishReason shouldBe FinishReason.NaturalStop: Unit
    result.finalAnswer.isLeft shouldBe true: Unit
    val err = result.finalAnswer.left.toOption.get
    err.rawAnswer should include("wrong"): Unit
    err.cause should not be null
  }

  it should "return Left(AgentParseError) on the maxIterations path where the capped answer is not schema-shaped" in {
    val dummyTool = AgentTool.fromFunction(
      "dummy",
      "Dummy tool"
    )((_: DummyInput) => "not json")

    val result = agentBuilder(
      AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "dummy", input = "{}")), StopReason.ToolUse)
    ).tools(dummyTool)
      .deriveResponseSchema[WeatherSummary]
      .build
      .runAs[WeatherSummary]("What's the weather?")(backend)

    result.finishReason shouldBe FinishReason.MaxIterations: Unit
    result.finalAnswer.isLeft shouldBe true: Unit
    result.finalAnswer.left.toOption.get.rawAnswer shouldBe "not json"
  }

  "Agent finish reason" should "be TokenLimit when the final answer is cut off by the token limit" in {
    val result = runLoop(agentBuilder(AgentResponse("partial answer", Seq.empty, StopReason.MaxTokens)))

    result.finishReason shouldBe FinishReason.TokenLimit
    result.finalAnswer shouldBe "partial answer"
    result.iterations shouldBe 1
  }

  "Agent with hooks" should "invoke afterToolCall once per tool call" in {
    val results = scala.collection.mutable.ListBuffer.empty[Any]

    runLoop(
      agentBuilder(
        AgentResponse(
          "",
          Seq(
            ToolCall(id = "call_1", toolName = "calculator", input = """{"a":5,"b":10}"""),
            ToolCall(id = "call_2", toolName = "calculator", input = """{"a":"bad","b":"input"}"""),
            ToolCall(id = "call_3", toolName = "non_existing_tool", input = """{"a":3,"b":7}""")
          ),
          StopReason.ToolUse
        ),
        AgentResponse("Done", Seq.empty, StopReason.EndTurn)
      ).tools(calculatorTool)
        .exceptionHandler(ExceptionHandler.sendAllToLLM)
        .hookBeforeToolCall { (c: ToolCall) =>
          results += c; ()
        }
        .hookAfterToolCall { (r: ToolCallRecord) =>
          results += r; ()
        }
    )

    results should contain inOrderOnly (
      ToolCall("call_1", "calculator", """{"a":5,"b":10}"""),
      ToolCallRecord("call_1", "calculator", """{"a":5,"b":10}""", "Result: 15.0", 1),
      ToolCall("call_2", "calculator", """{"a":"bad","b":"input"}"""),
      ToolCallRecord(
        "call_2",
        "calculator",
        """{"a":"bad","b":"input"}""",
        "Failed to parse arguments for tool 'calculator': DecodingFailure at .a: Double",
        1
      ),
      ToolCall("call_3", "non_existing_tool", """{"a":3,"b":7}"""),
      ToolCallRecord("call_3", "non_existing_tool", """{"a":3,"b":7}""", "Tool not found: non_existing_tool", 1)
    )
  }

  "AgentBuilder" should "accumulate configuration into the built config" in {
    val before: ToolCall => Identity[Unit] = _ => ()
    val after: ToolCallRecord => Identity[Unit] = _ => ()

    val config = agentBuilder()
      .maxIterations(7)
      .systemPrompt("custom")
      .tools(calculatorTool)
      .exceptionHandler(ExceptionHandler.sendAllToLLM)
      .hookBeforeToolCall(before)
      .hookAfterToolCall(after)
      .config

    config.maxIterations shouldBe 7
    config.systemPrompt.value shouldBe "custom"
    config.userTools should contain only calculatorTool
    config.exceptionHandler shouldBe ExceptionHandler.sendAllToLLM
    config.beforeToolCall.value shouldBe before
    config.afterToolCall.value shouldBe after
  }

  it should "derive the default system prompt from the final maxIterations" in {
    val systemPrompt = agentBuilder().maxIterations(42).config.systemPrompt

    systemPrompt.value should include("42")
  }

  it should "derive a response schema carrying the given description" in {
    val responseSchema = agentBuilder().deriveResponseSchema[WeatherSummary]("the weather report").config.responseSchema

    responseSchema.value.description shouldBe Some("the weather report")
  }
}
