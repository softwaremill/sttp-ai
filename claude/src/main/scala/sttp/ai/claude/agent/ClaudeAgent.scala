package sttp.ai.claude.agent

import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, Message, OutputConfig, OutputFormat, Tool}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.agent._
import sttp.client4.Backend
import io.circe.Json
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

  private[claude] val convertedTools: Seq[Tool] = tools.map(convertTool)

  private val outputConfig: Option[OutputConfig] =
    responseSchema.map(rs => OutputConfig(format = Some(OutputFormat.JsonSchema(rs.schema))))

  private def convertTool(tool: AgentTool[F, _]): Tool =
    Tool.CustomRaw(
      name = tool.name,
      description = tool.description,
      inputSchema = ensureObjectType(tool.rawJsonSchema)
    )

  /** Anthropic requires `input_schema.type == "object"`; MCP allows schemas that omit it (e.g. `{}` for no-argument tools), and also allows
    * the JSON Schema/MCP boolean form `true` (meaning "any input is valid") which Anthropic would reject outright. Both are normalized to a
    * minimal object schema; any other schema is passed through unchanged.
    */
  private def ensureObjectType(schema: Json): Json =
    if (schema.isBoolean) Json.obj("type" -> Json.fromString("object"))
    else
      schema.asObject match {
        case Some(obj) if !obj.contains("type") => Json.fromJsonObject(obj.add("type", Json.fromString("object")))
        case _                                  => schema
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
      backend: Backend[F],
      includeTools: Boolean
  ): F[AgentResponse] = {
    val messages = buildMessages(history)
    val request = MessageRequest(
      model = modelName,
      messages = messages.toList,
      maxTokens = 4096,
      system = systemPrompt,
      tools = if (includeTools && convertedTools.nonEmpty) Some(convertedTools.toList) else None,
      outputConfig = outputConfig
    )

    monad.flatMap(monad.map(client.createMessage(request).send(backend))(_.body)) {
      case Right(response) =>
        val textContent = response.content
          .collectFirst { case ContentBlock.Text(text, _, _) => text }
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
