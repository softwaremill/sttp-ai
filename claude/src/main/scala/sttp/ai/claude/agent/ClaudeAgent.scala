package sttp.ai.claude.agent

import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, Message, PropertySchema, Tool, ToolInputSchema}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.agent._
import sttp.client4.Backend

private[claude] class ClaudeAgentBackend[F[_]](
    client: ClaudeClient,
    modelName: String,
    val tools: Seq[AgentTool],
    val systemPrompt: Option[String]
)(implicit monad: sttp.monad.MonadError[F])
    extends AgentBackend[F] {

  private val convertedTools: Seq[Tool] = tools.map(convertTool)

  private def convertTool(tool: AgentTool): Tool = {
    val properties = tool.parameters.map { case (name, spec) =>
      name -> PropertySchema(
        `type` = ParameterType.asString(spec.dataType),
        description = Some(spec.description),
        `enum` = spec.`enum`.map(_.toList)
      )
    }

    val required = tool.parameters
      .filter { case (_, spec) => spec.required }
      .keys
      .toList

    Tool(
      name = tool.name,
      description = tool.description,
      inputSchema = ToolInputSchema(
        `type` = "object",
        properties = properties,
        required = if (required.nonEmpty) Some(required) else None
      )
    )
  }

  private def buildMessages(history: ConversationHistory): Seq[Message] =
    history.entries.flatMap {
      case ConversationEntry.UserPrompt(content) =>
        Some(Message.user(content))

      case ConversationEntry.AssistantResponse(content, toolCalls) =>
        val contentBlocks = if (content.nonEmpty) {
          List(ContentBlock.TextContent(content))
        } else List.empty

        val toolUseBlocks = toolCalls.map { tc =>
          ContentBlock.ToolUseContent(tc.id, tc.toolName, tc.input)
        }

        Some(Message.assistant(contentBlocks ++ toolUseBlocks))

      case ConversationEntry.ToolResult(toolCallId, _, result) =>
        Some(
          Message(
            role = "user",
            content = List(
              ContentBlock.ToolResultContent(
                toolUseId = toolCallId,
                content = result,
                isError = None
              )
            )
          )
        )

      case ConversationEntry.IterationMarker(current, max) =>
        Some(Message.user(s"[Iteration $current of $max]"))
    }

  override def sendRequest(
      history: ConversationHistory,
      backend: Backend[F]
  ): F[AgentResponse] = {
    val messages = buildMessages(history)
    val request = MessageRequest(
      model = modelName,
      messages = messages.toList,
      maxTokens = 4096,
      system = systemPrompt,
      tools = if (convertedTools.nonEmpty) Some(convertedTools.toList) else None
    )

    monad.flatMap(monad.map(client.createMessage(request).send(backend))(_.body)) {
      case Right(response) =>
        val textContent = response.content
          .collectFirst { case ContentBlock.TextContent(text) => text }
          .getOrElse("")

        val toolCalls = response.content.collect { case ContentBlock.ToolUseContent(id, name, input) =>
          ToolCall(id, name, input)
        }

        monad.unit(AgentResponse(textContent, toolCalls, response.stopReason))

      case Left(error) =>
        monad.error(
          new RuntimeException(s"Claude API error: ${error.getMessage}")
        )
    }
  }
}

object ClaudeAgent {
  def apply[F[_]](
      claudeConfig: ClaudeConfig,
      modelName: String,
      config: AgentConfig
  )(implicit monad: sttp.monad.MonadError[F]): Agent[F] = {
    val allTools = config.userTools ++ AgentConfig.systemTools
    val backend = new ClaudeAgentBackend[F](
      ClaudeClient(claudeConfig),
      modelName,
      allTools,
      config.systemPrompt
    )
    Agent(backend, config)
  }

  def apply[F[_]](
      client: ClaudeClient,
      modelName: String,
      config: AgentConfig
  )(implicit monad: sttp.monad.MonadError[F]): Agent[F] = {
    val allTools = config.userTools ++ AgentConfig.systemTools
    val backend = new ClaudeAgentBackend[F](
      client,
      modelName,
      allTools,
      config.systemPrompt
    )
    Agent(backend, config)
  }
}
