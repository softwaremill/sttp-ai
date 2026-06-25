package sttp.ai.openai.utils

import sttp.ai.openai.requests.completions.chat.ToolCall.FunctionToolCall
import sttp.ai.openai.requests.completions.chat.message.Tool.Function
import sttp.ai.openai.requests.completions.chat.message._
import sttp.ai.openai.requests.completions.chat.{FunctionCall, ToolCall}
import io.circe.Json
import io.circe.syntax.KeyOps

object ChatCompletionFixtures {
  def messages: Seq[Message] = systemMessages ++ userMessages ++ assistantMessages ++ toolMessages

  def systemMessages: Seq[Message.System] =
    Seq(Message.System("Hello!"), Message.System("Hello!", Some("User")))

  def userMessages: Seq[Message.User] = {
    val parts = Seq(
      Content.ContentPart.Text("Hello!"),
      Content.ContentPart.ImageUrl(Content.ImageUrlDetails("https://i.imgur.com/2tj5rQE.jpg"))
    )
    val arrayMessage = Message.User(Content.ArrayContent(parts))
    val stringMessage = Message.User(Content.TextContent("Hello!"), Some("User"))

    Seq(stringMessage, arrayMessage)
  }

  def assistantMessages: Seq[Message.Assistant] =
    Seq(
      Message.Assistant("Hello!", Some("User"), toolCalls),
      Message.Assistant("Hello!", Some("User")),
      Message.Assistant("Hello!")
    )

  def toolMessages: Seq[Message.Tool] =
    Seq(
      Message.Tool("Hello!", "tool_call_id_1"),
      Message.Tool("Hello!", "tool_call_id_2")
    )

  def tools: Seq[Tool] = {
    val function = Function(
      description = Some("Random description"),
      name = "Random name",
      parameters = Some(
        Map(
          "type" := "function",
          "properties" := Json.obj(
            "location" := Json.obj(
              "type" := "string",
              "description" := "The city and state e.g. San Francisco, CA"
            )
          ),
          "required" := Seq("location")
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
