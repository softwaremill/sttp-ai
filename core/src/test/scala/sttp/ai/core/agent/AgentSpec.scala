package sttp.ai.core.agent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.SyncBackendStub
import sttp.monad.IdentityMonad
import sttp.shared.Identity
import ujson.{Num, Str}

class AgentSpec extends AnyFlatSpec with Matchers {

  class StubAgentBackend(responses: Seq[AgentResponse]) extends AgentBackend[Identity] {
    private var callCount = 0
    var receivedHistories: Seq[ConversationHistory] = Seq.empty

    override def tools: Seq[AgentTool] = Seq.empty
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

  private val calculatorTool = AgentTool(
    toolName = "calculator",
    toolDescription = "Calculate",
    toolParameters = Map(
      "a" -> ParameterSpec(ParameterType.Number, "First number"),
      "b" -> ParameterSpec(ParameterType.Number, "Second number")
    )
  ) { input =>
    val a = input.get("a").map(_.num).getOrElse(0.0)
    val b = input.get("b").map(_.num).getOrElse(0.0)
    s"Result: ${a + b}"
  }

  private def createLoop(responses: Seq[AgentResponse], config: AgentConfig): (Agent[Identity], StubAgentBackend) = {
    val stubBackend = new StubAgentBackend(responses)
    val loop = new Agent[Identity](stubBackend, config)(IdentityMonad)
    (loop, stubBackend)
  }

  private def runLoop(responses: Seq[AgentResponse], tools: Seq[AgentTool] = Seq.empty): AgentResult = {
    val testConfig = AgentConfig(maxIterations = 5, userTools = tools).right.get
    val (loop, _) = createLoop(responses, testConfig)
    loop.run("Test")(backend)
  }

  "Agent" should "stop after max iterations" in {
    val dummyTool = AgentTool(
      toolName = "dummy",
      toolDescription = "Dummy tool",
      toolParameters = Map.empty
    )(_ => "dummy result")

    val result = runLoop(
      Seq(
        AgentResponse("", Seq(ToolCall(id = "call_1", toolName = "dummy", input = Map.empty)), Some("tool_calls")),
        AgentResponse("", Seq(ToolCall(id = "call_2", toolName = "dummy", input = Map.empty)), Some("tool_calls")),
        AgentResponse("", Seq(ToolCall(id = "call_3", toolName = "dummy", input = Map.empty)), Some("tool_calls")),
        AgentResponse("", Seq(ToolCall(id = "call_4", toolName = "dummy", input = Map.empty)), Some("tool_calls")),
        AgentResponse("", Seq(ToolCall(id = "call_5", toolName = "dummy", input = Map.empty)), Some("tool_calls"))
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
            ToolCall(id = "call_1", toolName = "finish", input = Map("answer" -> Str("Final answer")))
          ),
          Some("tool_calls")
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
        AgentResponse("Final answer without tools", Seq.empty, Some("stop"))
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
            ToolCall(id = "call_1", toolName = "calculator", input = Map("a" -> Num(5), "b" -> Num(10))),
            ToolCall(id = "call_2", toolName = "calculator", input = Map("a" -> Num(3), "b" -> Num(7)))
          ),
          Some("tool_calls")
        ),
        AgentResponse("Done", Seq.empty, Some("stop"))
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
    val errorTool = AgentTool(
      toolName = "error_tool",
      toolDescription = "Tool that throws",
      toolParameters = Map.empty
    ) { _ =>
      throw new RuntimeException("Tool error")
    }

    val result = runLoop(
      Seq(
        AgentResponse(
          "",
          Seq(
            ToolCall(id = "call_1", toolName = "error_tool", input = Map.empty)
          ),
          Some("tool_calls")
        ),
        AgentResponse("Recovered", Seq.empty, Some("stop"))
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
            ToolCall(id = "call_1", toolName = "unknown_tool", input = Map.empty)
          ),
          Some("tool_calls")
        ),
        AgentResponse("Recovered", Seq.empty, Some("stop"))
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
            ToolCall(id = "call_1", toolName = "calculator", input = Map("a" -> Num(5), "b" -> Num(10)))
          ),
          Some("tool_calls")
        ),
        AgentResponse("Done", Seq.empty, Some("stop"))
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
        AgentResponse("First", Seq.empty, Some("stop")),
        AgentResponse("Second", Seq.empty, Some("stop"))
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
            ToolCall(id = "call_1", toolName = "calculator", input = Map("a" -> Num(5), "b" -> Num(10))),
            ToolCall(id = "call_2", toolName = "finish", input = Map("answer" -> Str("Early finish")))
          ),
          Some("tool_calls")
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
    val userFinishTool = AgentTool(
      toolName = "finish",
      toolDescription = "User finish tool",
      toolParameters = Map.empty
    )(_ => "custom finish")

    val configResult = AgentConfig(userTools = Seq(userFinishTool))

    configResult shouldBe a[Left[_, _]]
    configResult.left.toOption.get should include("reserved names")
    configResult.left.toOption.get should include("finish")
    configResult.left.toOption.get should include("system tools")
  }

  it should "accept valid user tools" in {
    val customTool = AgentTool(
      toolName = "custom",
      toolDescription = "Custom tool",
      toolParameters = Map.empty
    )(_ => "result")

    val configResult = AgentConfig(userTools = Seq(customTool))

    configResult shouldBe a[Right[_, _]]
    val config = configResult.toOption.get
    config.userTools should have size 1
    config.userTools.head.name shouldBe "custom"
  }

  it should "extract final answer from last tool result on max iterations" in {
    val dummyTool = AgentTool(
      toolName = "dummy",
      toolDescription = "Dummy tool",
      toolParameters = Map.empty
    )(_ => "final tool result")

    val config = AgentConfig(maxIterations = 3, userTools = Seq(dummyTool)).right.get
    val (loop, _) = createLoop(
      Seq(
        AgentResponse("First response", Seq(ToolCall(id = "call_1", toolName = "dummy", input = Map.empty)), Some("tool_calls")),
        AgentResponse("Second response", Seq(ToolCall(id = "call_2", toolName = "dummy", input = Map.empty)), Some("tool_calls")),
        AgentResponse("Third response", Seq(ToolCall(id = "call_3", toolName = "dummy", input = Map.empty)), Some("tool_calls"))
      ),
      config
    )

    val result = loop.run("Test")(backend)

    result.finishReason shouldBe FinishReason.MaxIterations
    result.finalAnswer shouldBe "final tool result"
    result.iterations shouldBe 3
  }
}
