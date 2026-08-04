package sttp.ai.core.agent.testing

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.agent.{AgentResponse, StopReason, ToolCall}

class ScriptedResponseSpec extends AnyFlatSpec with Matchers {

  "ScriptedResponse.text" should "create an end-turn response with no tool calls" in {
    ScriptedResponse.text("done") shouldBe AgentResponse("done", Seq.empty, StopReason.EndTurn)
  }

  "ScriptedResponse.toolCall" should "create a tool-use response with a single generated id" in {
    ScriptedResponse.toolCall("calculator", """{"a": 1}""") shouldBe AgentResponse(
      "",
      Seq(ToolCall("call_1", "calculator", """{"a": 1}""")),
      StopReason.ToolUse
    )
  }

  "ScriptedResponse.toolCalls" should "number the generated call ids in order" in {
    ScriptedResponse.toolCalls("a" -> "{}", "b" -> """{"x": 1}""") shouldBe AgentResponse(
      "",
      Seq(ToolCall("call_1", "a", "{}"), ToolCall("call_2", "b", """{"x": 1}""")),
      StopReason.ToolUse
    )
  }

  "ScriptedResponse.textWithToolCalls" should "carry both text and tool calls" in {
    ScriptedResponse.textWithToolCalls("thinking...", "a" -> "{}") shouldBe AgentResponse(
      "thinking...",
      Seq(ToolCall("call_1", "a", "{}")),
      StopReason.ToolUse
    )
  }

  "ScriptedResponse.maxTokens" should "create a max-tokens response" in {
    ScriptedResponse.maxTokens("truncated") shouldBe AgentResponse("truncated", Seq.empty, StopReason.MaxTokens)
  }
}
