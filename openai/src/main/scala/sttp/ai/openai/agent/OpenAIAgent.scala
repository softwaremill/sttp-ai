package sttp.ai.openai.agent

import sttp.ai.core.agent._
import sttp.ai.core.json.SnakePickle
import sttp.ai.openai.OpenAI
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.{FunctionCall, SchemaSupport, ToolCall => OpenAIToolCall}
import sttp.ai.openai.requests.completions.chat.message.{Content, Message, Tool}
import sttp.client4.Backend
import ujson.Value

private[openai] class OpenAIAgentBackend[F[_]](
    openAI: OpenAI,
    modelName: String,
    val tools: Seq[AgentTool[_]],
    val systemPrompt: Option[String]
)(implicit monad: sttp.monad.MonadError[F])
    extends AgentBackend[F] {

  private val convertedTools: Seq[Tool.FunctionTool] = tools.map(convertTool)

  private def convertTool(tool: AgentTool[_]): Tool.FunctionTool = {
    val schema = tool.jsonSchema
    val schemaJson = SnakePickle.writeJs(schema)(SchemaSupport.schemaRW)

    Tool.FunctionTool(
      name = tool.name,
      description = Some(tool.description),
      parameters = Some(schemaJson.obj.toMap),
      strict = Some(false)
    )
  }

  private def buildMessages(history: ConversationHistory): Seq[Message] = {
    val systemMessages = systemPrompt.map { prompt =>
      Message.SystemMessage(content = prompt)
    }.toSeq

    val conversationMessages = history.entries.flatMap {
      case ConversationEntry.UserPrompt(content) =>
        Seq(Message.UserMessage(content = Content.TextContent(content)))

      case ConversationEntry.AssistantResponse(content, toolCalls) =>
        val openaiToolCalls = toolCalls.map { tc =>
          val argsJson = SnakePickle.write(tc.input)
          OpenAIToolCall.FunctionToolCall(
            id = Some(tc.id),
            function = FunctionCall(
              arguments = argsJson,
              name = Some(tc.toolName)
            )
          )
        }
        Seq(
          Message.AssistantMessage(
            content = content,
            toolCalls = openaiToolCalls
          )
        )

      case ConversationEntry.ToolResult(toolCallId, _, result) =>
        Seq(Message.ToolMessage(content = result, toolCallId = toolCallId))

      case ConversationEntry.IterationMarker(current, max) =>
        Seq(Message.UserMessage(content = Content.TextContent(s"[Iteration $current of $max]")))
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
      tools = if (convertedTools.nonEmpty) Some(convertedTools) else None
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
                  val inputMap =
                    try {
                      val parsed = ujson.read(function.arguments)
                      parsed.obj.toMap
                    } catch {
                      case _: Exception => Map.empty[String, Value]
                    }
                  ToolCall(
                    id = id,
                    toolName = function.name.getOrElse("unknown"),
                    input = inputMap
                  )
              }
            }
          case None => Seq.empty
        }

        val stopReason = response.choices.headOption.map(_.finishReason)

        monad.unit(AgentResponse(textContent, toolCalls, stopReason))

      case Left(error) =>
        monad.error(
          new RuntimeException(s"OpenAI API error: ${error.getMessage}")
        )
    }
  }
}

object OpenAIAgent {
  def apply[F[_]](
      apiKey: String,
      modelName: String,
      config: AgentConfig
  )(implicit monad: sttp.monad.MonadError[F]): Agent[F] = {
    val allTools = config.userTools ++ AgentConfig.systemTools
    val backend = new OpenAIAgentBackend[F](
      new OpenAI(apiKey),
      modelName,
      allTools,
      config.systemPrompt
    )
    Agent(backend, config)
  }

  def apply[F[_]](
      openAI: OpenAI,
      modelName: String,
      config: AgentConfig
  )(implicit monad: sttp.monad.MonadError[F]): Agent[F] = {
    val allTools = config.userTools ++ AgentConfig.systemTools
    val backend = new OpenAIAgentBackend[F](
      openAI,
      modelName,
      allTools,
      config.systemPrompt
    )
    Agent(backend, config)
  }
}
