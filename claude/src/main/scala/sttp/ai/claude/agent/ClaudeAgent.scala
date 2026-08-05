package sttp.ai.claude.agent

import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ClaudeModel, ContentBlock, Message, OutputConfig, OutputFormat, Tool}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.agent._
import sttp.client4.Backend
import io.circe.Json
import io.circe.parser.{parse => parseJson}
import sttp.shared.Identity
import sttp.monad.IdentityMonad

private[claude] class ClaudeAgentBackend[F[_]](
    client: ClaudeClient,
    modelForIteration: IterationInfo => ClaudeModel,
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
      inputSchema = AgentTool.ensureObjectType(tool.rawJsonSchema)
    )

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
      includeTools: Boolean,
      iterationInfo: IterationInfo
  ): F[AgentResponse] = {
    val messages = buildMessages(history)
    val request = MessageRequest(
      model = modelForIteration(iterationInfo).value,
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

  /** Entry point: `ClaudeAgent.builder[F](client, model)`. The indirection lets `M` be inferred while `F` is given explicitly. */
  def builder[F[_]]: BuilderPartiallyApplied[F] = new BuilderPartiallyApplied[F]

  final class BuilderPartiallyApplied[F[_]] private[ClaudeAgent] () {

    def apply[M <: ClaudeModel](client: ClaudeClient, model: M)(implicit monad: sttp.monad.MonadError[F]): AgentBuilder[F, M] =
      apply(client, (_: IterationInfo) => model)

    /** Selects the model per loop iteration (e.g. a cheap model for tool iterations, a stronger one for the forced-final synthesis). `M`
      * infers to the least upper bound of every model the function can return, so capability checks require the shared capabilities.
      */
    def apply[M <: ClaudeModel](client: ClaudeClient, modelForIteration: IterationInfo => M)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, M] =
      AgentBuilder[F, M](config =>
        new ClaudeAgentBackend[F](client, modelForIteration, config.userTools, config.systemPrompt, config.responseSchema)
      )

    def apply(client: ClaudeClient, modelName: String)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, ClaudeModel.CustomClaudeModel] =
      apply(client, ClaudeModel.CustomClaudeModel(modelName))

    def apply[M <: ClaudeModel](claudeConfig: ClaudeConfig, model: M)(implicit monad: sttp.monad.MonadError[F]): AgentBuilder[F, M] =
      apply(ClaudeClient(claudeConfig), model)

    def apply[M <: ClaudeModel](claudeConfig: ClaudeConfig, modelForIteration: IterationInfo => M)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, M] =
      apply(ClaudeClient(claudeConfig), modelForIteration)

    def apply(claudeConfig: ClaudeConfig, modelName: String)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, ClaudeModel.CustomClaudeModel] =
      apply(ClaudeClient(claudeConfig), modelName)
  }

  def synchronous[M <: ClaudeModel](client: ClaudeClient, model: M): AgentBuilder[Identity, M] =
    builder[Identity](client, model)(IdentityMonad)

  def synchronous[M <: ClaudeModel](client: ClaudeClient, modelForIteration: IterationInfo => M): AgentBuilder[Identity, M] =
    builder[Identity](client, modelForIteration)(IdentityMonad)

  def synchronous(client: ClaudeClient, modelName: String): AgentBuilder[Identity, ClaudeModel.CustomClaudeModel] =
    builder[Identity](client, modelName)(IdentityMonad)

  def synchronous[M <: ClaudeModel](claudeConfig: ClaudeConfig, model: M): AgentBuilder[Identity, M] =
    builder[Identity](claudeConfig, model)(IdentityMonad)

  def synchronous[M <: ClaudeModel](claudeConfig: ClaudeConfig, modelForIteration: IterationInfo => M): AgentBuilder[Identity, M] =
    builder[Identity](claudeConfig, modelForIteration)(IdentityMonad)

  def synchronous(claudeConfig: ClaudeConfig, modelName: String): AgentBuilder[Identity, ClaudeModel.CustomClaudeModel] =
    builder[Identity](claudeConfig, modelName)(IdentityMonad)
}
