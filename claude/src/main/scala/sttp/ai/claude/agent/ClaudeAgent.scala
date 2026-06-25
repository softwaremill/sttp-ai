package sttp.ai.claude.agent

import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, Message, OutputConfig, OutputFormat, PropertySchema, Tool, ToolInputSchema}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.agent._
import sttp.apispec.circe._
import sttp.client4.Backend
import io.circe.Json
import io.circe.syntax._
import io.circe.parser.{parse => parseJson}
import sttp.shared.Identity
import sttp.monad.IdentityMonad

private[claude] class ClaudeAgentBackend[F[_]](
    client: ClaudeClient,
    modelName: String,
    val tools: Seq[AgentTool[F, _]],
    val systemPrompt: Option[String],
    responseSchema: Option[ResponseSchema[_]]
)(implicit monad: sttp.monad.MonadError[F])
    extends AgentBackend[F] {

  private val convertedTools: Seq[Tool] = tools.map(convertTool)

  private val outputConfig: Option[OutputConfig] =
    responseSchema.map(rs => OutputConfig(format = Some(OutputFormat.JsonSchema(rs.schema))))

  private def convertTool(tool: AgentTool[F, _]): Tool = {
    val schemaCursor = tool.jsonSchema.asJson.hcursor

    val properties = schemaCursor
      .downField("properties")
      .focus
      .flatMap(_.asObject)
      .map { propsObj =>
        propsObj.toMap.map { case (name, propSchema) =>
          val c = propSchema.hcursor
          val propType = c.get[String]("type").toOption.getOrElse("string")
          val propDescription = c.get[String]("description").toOption
          val propEnum = c.downField("enum").as[List[String]].toOption

          name -> PropertySchema(
            `type` = propType,
            description = propDescription,
            `enum` = propEnum
          )
        }
      }
      .getOrElse(Map.empty)

    val required = schemaCursor.downField("required").as[List[String]].toOption

    Tool(
      name = tool.name,
      description = tool.description,
      inputSchema = ToolInputSchema(
        `type` = "object",
        properties = properties,
        required = required
      )
    )
  }

  private def buildMessages(history: ConversationHistory): Seq[Message] =
    history.entries.flatMap {
      case ConversationEntry.UserPrompt(content) =>
        Some(Message.user(content))

      case ConversationEntry.AssistantResponse(content, toolCalls) =>
        val contentBlocks = if (content.nonEmpty) {
          List(ContentBlock.Text(content))
        } else List.empty

        val toolUseBlocks = toolCalls.map { tc =>
          val input = parseJson(tc.input).flatMap(_.as[Map[String, Json]]).fold(throw _, identity)
          ContentBlock.ToolUse(tc.id, tc.toolName, input)
        }

        Some(Message.assistant(contentBlocks ++ toolUseBlocks))

      case ConversationEntry.ToolResult(toolCallId, _, result) =>
        Some(
          Message(
            role = "user",
            content = List(
              ContentBlock.ToolResult(
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
      tools = if (convertedTools.nonEmpty) Some(convertedTools.toList) else None,
      outputConfig = outputConfig
    )

    monad.flatMap(monad.map(client.createMessage(request).send(backend))(_.body)) {
      case Right(response) =>
        val textContent = response.content
          .collectFirst { case ContentBlock.Text(text, _) => text }
          .getOrElse("")

        val toolCalls = response.content.collect { case ContentBlock.ToolUse(id, name, input) =>
          val inputJson = Json.fromFields(input).noSpaces
          ToolCall(id, name, inputJson)
        }

        val stopReason = mapClaudeStopReason(response.stopReason)
        monad.unit(AgentResponse(textContent, toolCalls, stopReason))

      case Left(error) =>
        monad.error(
          new RuntimeException(s"Claude API error: ${error.getMessage}")
        )
    }
  }

  private def mapClaudeStopReason(reason: Option[String]): StopReason =
    reason match {
      case Some("end_turn")      => StopReason.EndTurn
      case Some("tool_use")      => StopReason.ToolUse
      case Some("max_tokens")    => StopReason.MaxTokens
      case Some("stop_sequence") => StopReason.StopSequence
      case Some(other)           => StopReason.Other(other)
      case None                  => StopReason.EndTurn
    }
}

object ClaudeAgent {

  def builder[F[_]](
      claudeConfig: ClaudeConfig,
      modelName: String
  )(implicit monad: sttp.monad.MonadError[F]): AgentBuilder[F] =
    builder(ClaudeClient(claudeConfig), modelName)

  def builder[F[_]](
      client: ClaudeClient,
      modelName: String
  )(implicit monad: sttp.monad.MonadError[F]): AgentBuilder[F] =
    AgentBuilder[F](config => new ClaudeAgentBackend[F](client, modelName, config.userTools, config.systemPrompt, config.responseSchema))

  def synchronous(
      claudeConfig: ClaudeConfig,
      modelName: String
  ): AgentBuilder[Identity] = builder[Identity](claudeConfig, modelName)(IdentityMonad)

  def synchronous(
      client: ClaudeClient,
      modelName: String
  ): AgentBuilder[Identity] = builder[Identity](client, modelName)(IdentityMonad)
}
