package sttp.openai.utils

import sttp.openai.requests.completions.chat.ToolCall.FunctionToolCall
import sttp.openai.requests.completions.chat.message.Tool.FunctionTool
import sttp.openai.requests.completions.chat.message._
import sttp.openai.requests.completions.chat.{FunctionCall, ToolCall}
import ujson._

object ChatCompletionFixtures {
  def messages: Seq[Message] = systemMessages ++ userMessages ++ assistantMessages ++ toolMessages

  def systemMessages: Seq[Message.SystemMessage] =
    Seq(Message.SystemMessage("Hello!"), Message.SystemMessage("Hello!", Some("User")))

  def userMessages: Seq[Message.UserMessage] = {
    val parts = Seq(
      Content.TextContentPart("Hello!"),
      Content.ImageContentPart(Content.ImageUrl("https://i.imgur.com/2tj5rQE.jpg"))
    )
    val arrayMessage = Message.UserMessage(Content.ArrayContent(parts))
    val stringMessage = Message.UserMessage(Content.TextContent("Hello!"), Some("User"))

    Seq(stringMessage, arrayMessage)
  }

  def assistantMessages: Seq[Message.AssistantMessage] =
    Seq(
      Message.AssistantMessage("Hello!", Some("User"), toolCalls),
      Message.AssistantMessage("Hello!", Some("User")),
      Message.AssistantMessage("Hello!")
    )

  def toolMessages: Seq[Message.ToolMessage] =
    Seq(
      Message.ToolMessage("Hello!", "tool_call_id_1"),
      Message.ToolMessage("Hello!", "tool_call_id_2")
    )

  def tools: Seq[Tool] = {
    val function = FunctionTool(
      description = Some("Random description"),
      name = "Random name",
      parameters = Some(
        Map(
          "type" -> Str("function"),
          "properties" -> Obj(
            "location" -> Obj(
              "type" -> "string",
              "description" -> "The city and state e.g. San Francisco, CA"
            )
          ),
          "required" -> Arr("location")
        )
      )
    )

    Seq(function)
  }

  def toolCalls: Seq[ToolCall] =
    Seq(
      FunctionToolCall(
        None,
        FunctionCall(
          arguments = "args"
        )
      ),
      FunctionToolCall(
        Some("tool_id_2"),
        FunctionCall(
          arguments = "args",
          name = Some("Fish")
        )
      )
    )
}
