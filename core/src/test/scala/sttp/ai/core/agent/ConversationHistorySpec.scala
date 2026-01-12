package sttp.ai.core.agent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ujson.Str

class ConversationHistorySpec extends AnyFlatSpec with Matchers {

  "empty" should "create empty conversation history" in {
    val history = ConversationHistory.empty

    history.entries shouldBe empty
  }

  "withInitialPrompt" should "create history with initial user prompt" in {
    val history = ConversationHistory.withInitialPrompt("Hello")

    history.entries should have size 1
    history.entries.head shouldBe ConversationEntry.UserPrompt("Hello")
  }

  "addUserPrompt" should "add user prompt to history" in {
    val history = ConversationHistory.empty
      .addUserPrompt("First message")

    history.entries should have size 1
    history.entries.head shouldBe ConversationEntry.UserPrompt("First message")
  }

  it should "preserve existing entries" in {
    val history = ConversationHistory
      .withInitialPrompt("First")
      .addUserPrompt("Second")

    history.entries should have size 2
    history.entries(0) shouldBe ConversationEntry.UserPrompt("First")
    history.entries(1) shouldBe ConversationEntry.UserPrompt("Second")
  }

  "addAssistantResponse" should "add assistant response without tool calls" in {
    val history = ConversationHistory.empty
      .addAssistantResponse("Hello back", Seq.empty)

    history.entries should have size 1
    history.entries.head shouldBe ConversationEntry.AssistantResponse("Hello back", Seq.empty)
  }

  it should "add assistant response with tool calls" in {
    val toolCall = ToolCall(id = "call_1", toolName = "test", input = Map("x" -> Str("5")))
    val history = ConversationHistory.empty
      .addAssistantResponse("Let me check", Seq(toolCall))

    history.entries should have size 1
    val entry = history.entries.head
    entry shouldBe a[ConversationEntry.AssistantResponse]
    val assistantResponse = entry.asInstanceOf[ConversationEntry.AssistantResponse]
    assistantResponse.content shouldBe "Let me check"
    assistantResponse.toolCalls should have size 1
    assistantResponse.toolCalls.head shouldBe toolCall
  }

  it should "preserve existing entries" in {
    val history = ConversationHistory
      .withInitialPrompt("User message")
      .addAssistantResponse("Response", Seq.empty)

    history.entries should have size 2
    history.entries(0) shouldBe ConversationEntry.UserPrompt("User message")
    history.entries(1) shouldBe ConversationEntry.AssistantResponse("Response", Seq.empty)
  }

  "addToolResult" should "add tool result to history" in {
    val history = ConversationHistory.empty
      .addToolResult("call_1", "calculator", "Result: 42")

    history.entries should have size 1
    history.entries.head shouldBe ConversationEntry.ToolResult("call_1", "calculator", "Result: 42")
  }

  it should "preserve existing entries" in {
    val toolCall = ToolCall(id = "call_1", toolName = "calc", input = Map("x" -> Str("5")))
    val history = ConversationHistory.empty
      .addAssistantResponse("Calculating", Seq(toolCall))
      .addToolResult("call_1", "calc", "5")

    history.entries should have size 2
    history.entries(0) shouldBe a[ConversationEntry.AssistantResponse]
    history.entries(1) shouldBe ConversationEntry.ToolResult("call_1", "calc", "5")
  }

  "addIterationMarker" should "add iteration marker to history" in {
    val history = ConversationHistory.empty
      .addIterationMarker(2, 10)

    history.entries should have size 1
    history.entries.head shouldBe ConversationEntry.IterationMarker(2, 10)
  }

  it should "preserve existing entries" in {
    val history = ConversationHistory
      .withInitialPrompt("Start")
      .addIterationMarker(1, 5)
      .addIterationMarker(2, 5)

    history.entries should have size 3
    history.entries(0) shouldBe ConversationEntry.UserPrompt("Start")
    history.entries(1) shouldBe ConversationEntry.IterationMarker(1, 5)
    history.entries(2) shouldBe ConversationEntry.IterationMarker(2, 5)
  }

  "ConversationHistory" should "be immutable" in {
    val original = ConversationHistory.withInitialPrompt("Original")
    val modified = original.addUserPrompt("Modified")

    original.entries should have size 1
    modified.entries should have size 2
    original.entries.head shouldBe ConversationEntry.UserPrompt("Original")
  }

  it should "handle complex conversation flow" in {
    val toolCall1 = ToolCall(id = "call_1", toolName = "calc", input = Map("x" -> Str("5")))
    val toolCall2 = ToolCall(id = "call_2", toolName = "weather", input = Map("city" -> Str("Paris")))

    val history = ConversationHistory
      .withInitialPrompt("Calculate 5+10 and get weather")
      .addAssistantResponse("I'll help with that", Seq(toolCall1, toolCall2))
      .addToolResult("call_1", "calc", "15")
      .addToolResult("call_2", "weather", "Sunny, 22C")
      .addIterationMarker(2, 5)
      .addAssistantResponse("The result is 15 and it's sunny in Paris", Seq.empty)

    history.entries should have size 6
    history.entries(0) shouldBe ConversationEntry.UserPrompt("Calculate 5+10 and get weather")
    history.entries(1) shouldBe a[ConversationEntry.AssistantResponse]
    history.entries(2) shouldBe ConversationEntry.ToolResult("call_1", "calc", "15")
    history.entries(3) shouldBe ConversationEntry.ToolResult("call_2", "weather", "Sunny, 22C")
    history.entries(4) shouldBe ConversationEntry.IterationMarker(2, 5)
    history.entries(5) shouldBe ConversationEntry.AssistantResponse("The result is 15 and it's sunny in Paris", Seq.empty)
  }

  "ToolCall" should "store id, toolName, and input correctly" in {
    val input = Map("param1" -> Str("value1"), "param2" -> Str("value2"))
    val toolCall = ToolCall(id = "call_123", toolName = "myTool", input = input)

    toolCall.id shouldBe "call_123"
    toolCall.toolName shouldBe "myTool"
    toolCall.input shouldBe input
  }
}
