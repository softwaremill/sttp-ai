package sttp.ai.openai.agent

import sttp.ai.core.agent._
import sttp.ai.openai.OpenAI
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel, ResponseFormat}
import sttp.ai.openai.requests.completions.chat.message.{Content, Message, Tool}
import sttp.ai.openai.requests.completions.chat.{FunctionCall, SchemaSupport, ToolCall => OpenAIToolCall}
import sttp.client4.Backend
import sttp.shared.Identity
import sttp.monad.IdentityMonad

private[openai] class OpenAIAgentBackend[F[_]](
    openAI: OpenAI,
    modelName: String,
    val tools: Seq[AgentTool[F, _]],
    val systemPrompt: Option[String],
    responseSchema: Option[ResponseSchema[_]],
    strictTools: Boolean
)(implicit monad: sttp.monad.MonadError[F])
    extends AgentBackend[F] {

  private[openai] val convertedTools: Seq[Tool.Function] = tools.map(convertTool)

  private val responseFormat: Option[ResponseFormat] = responseSchema.map { rs =>
    ResponseFormat.JsonSchema(
      name = "final_response",
      strict = Some(true),
      schema = Some(rs.schema),
      description = rs.description
    )
  }

  private def convertTool(tool: AgentTool[F, _]): Tool.Function = {
    val schemaJson =
      if (strictTools) SchemaSupport.normalizeForStrict(tool.rawJsonSchema)
      else tool.rawJsonSchema

    Tool.Function(
      name = tool.name,
      description = Some(tool.description),
      parameters = Some(schemaJson.asObject.map(_.toMap).getOrElse(Map.empty)),
      strict = Some(strictTools)
    )
  }

  private def buildMessages(history: ConversationHistory): Seq[Message] = {
    val systemMessages = systemPrompt.map { prompt =>
      Message.System(content = prompt)
    }.toSeq

    val conversationMessages = history.entries.flatMap {
      case ConversationEntry.UserPrompt(content) =>
        Seq(Message.User(content = Content.TextContent(content)))

      case ConversationEntry.AssistantResponse(content, toolCalls) =>
        val openaiToolCalls = toolCalls.map { tc =>
          OpenAIToolCall.FunctionToolCall(
            id = Some(tc.id),
            function = FunctionCall(
              arguments = tc.input,
              name = Some(tc.toolName)
            )
          )
        }
        Seq(
          Message.Assistant(
            content = content,
            toolCalls = openaiToolCalls
          )
        )

      case ConversationEntry.ToolResult(toolCallId, _, result) =>
        Seq(Message.Tool(content = result, toolCallId = toolCallId))

      case ConversationEntry.IterationMarker(current, max) =>
        Seq(Message.User(content = Content.TextContent(s"[Iteration $current of $max]")))
    }

    systemMessages ++ conversationMessages
  }

  override def sendRequest(
      history: ConversationHistory,
      backend: Backend[F]
  ): F[AgentResponse] = {
    val messages = buildMessages(history)
    val request = ChatBody(
      model = ChatCompletionModel.CustomChatCompletionModel(modelName),
      messages = messages,
      tools = if (convertedTools.nonEmpty) Some(convertedTools) else None,
      responseFormat = responseFormat
    )
    monad.flatMap(monad.map(openAI.createChatCompletion(request).send(backend))(_.body)) {
      case Right(response) =>
        val textContent = response.choices.headOption
          .flatMap(choice => Option(choice.message.content))
          .getOrElse("")

        val toolCalls = response.choices.headOption match {
          case Some(choice) =>
            choice.message.toolCalls.zipWithIndex.map { case (toolCall, idx) =>
              toolCall match {
                case OpenAIToolCall.FunctionToolCall(maybeId, function) =>
                  val id = maybeId.getOrElse(s"call_$idx")
                  ToolCall(
                    id = id,
                    toolName = function.name.getOrElse("unknown"),
                    input = function.arguments
                  )
              }
            }
          case None => Seq.empty
        }

        val stopReason = response.choices.headOption
          .map(_.finishReason)
          .map(mapOpenAIStopReason)
          .getOrElse(StopReason.EndTurn)

        monad.unit(AgentResponse(textContent, toolCalls, stopReason))

      case Left(error) =>
        monad.error(
          new RuntimeException(s"OpenAI API error: ${error.getMessage}")
        )
    }
  }

  private def mapOpenAIStopReason(reason: String): StopReason =
    reason match {
      case "stop"           => StopReason.EndTurn
      case "tool_calls"     => StopReason.ToolUse
      case "function_calls" => StopReason.ToolUse
      case "length"         => StopReason.MaxTokens
      case "content_filter" => StopReason.ContentFilter
      case other            => StopReason.Other(other)
    }
}

object OpenAIAgent {

  def builder[F[_]](
      openAI: OpenAI,
      modelName: String
  )(implicit monad: sttp.monad.MonadError[F]): AgentBuilder[F] =
    builder(openAI, modelName, strictTools = true)

  def builder[F[_]](
      apiKey: String,
      modelName: String
  )(implicit monad: sttp.monad.MonadError[F]): AgentBuilder[F] =
    builder(apiKey, modelName, strictTools = true)

  def synchronous(
      openAI: OpenAI,
      modelName: String
  ): AgentBuilder[Identity] = synchronous(openAI, modelName, strictTools = true)

  def synchronous(
      apiKey: String,
      modelName: String
  ): AgentBuilder[Identity] = synchronous(apiKey, modelName, strictTools = true)

  def builder[F[_]](
      openAI: OpenAI,
      modelName: String,
      strictTools: Boolean
  )(implicit monad: sttp.monad.MonadError[F]): AgentBuilder[F] =
    AgentBuilder[F](config =>
      new OpenAIAgentBackend[F](openAI, modelName, config.userTools, config.systemPrompt, config.responseSchema, strictTools)
    )

  def builder[F[_]](
      apiKey: String,
      modelName: String,
      strictTools: Boolean
  )(implicit monad: sttp.monad.MonadError[F]): AgentBuilder[F] =
    builder(new OpenAI(apiKey), modelName, strictTools)

  def synchronous(
      openAI: OpenAI,
      modelName: String,
      strictTools: Boolean
  ): AgentBuilder[Identity] = builder[Identity](openAI, modelName, strictTools)(IdentityMonad)

  def synchronous(
      apiKey: String,
      modelName: String,
      strictTools: Boolean
  ): AgentBuilder[Identity] = builder[Identity](apiKey, modelName, strictTools)(IdentityMonad)
}
