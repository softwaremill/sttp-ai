package sttp.ai.gemini.agent

import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.models.{Content, InteractionInput, InteractionStatus, ResponseFormat, Step, Tool}
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.responses.InteractionResponse
import sttp.ai.core.agent._
import sttp.apispec.circe._
import sttp.client4.Backend
import io.circe.Json
import io.circe.syntax._
import io.circe.parser.{parse => parseJson}
import sttp.shared.Identity
import sttp.monad.IdentityMonad

private[gemini] class GeminiAgentBackend[F[_]](
    client: GeminiClient,
    modelName: String,
    val tools: Seq[AgentTool[F, _]],
    val systemPrompt: Option[String],
    responseSchema: Option[ResponseSchema[_]]
)(implicit monad: sttp.monad.MonadError[F])
    extends AgentBackend[F] {

  private[gemini] val convertedTools: Seq[Tool] = tools.map(convertTool)

  private val responseFormat: Option[ResponseFormat] =
    responseSchema.map(rs => ResponseFormat.JsonSchema(rs.schema.asJson.deepDropNullValues))

  private def convertTool(tool: AgentTool[F, _]): Tool =
    Tool.Function(
      name = tool.name,
      description = Some(tool.description),
      parameters = ensureObjectType(tool.rawJsonSchema)
    )

  /** Gemini function declarations require an object parameters schema; MCP allows schemas that omit `type` (e.g. `{}` for no-argument
    * tools) and the JSON Schema/MCP boolean form `true` ("any input is valid"). Both are normalized to a minimal object schema; any other
    * schema is passed through unchanged.
    */
  private def ensureObjectType(schema: Json): Json =
    if (schema.isBoolean) Json.obj("type" -> Json.fromString("object"))
    else
      schema.asObject match {
        case Some(obj) if !obj.contains("type") => Json.fromJsonObject(obj.add("type", Json.fromString("object")))
        case _                                  => schema
      }

  private def buildSteps(history: ConversationHistory): List[Step] =
    history.entries.flatMap {
      case ConversationEntry.UserPrompt(content) =>
        List(Step.userText(content))

      case ConversationEntry.AssistantResponse(content, toolCalls) =>
        val outputStep = if (content.nonEmpty) List(Step.ModelOutput(List(Content.Text(content)))) else List.empty
        val callSteps = toolCalls.map { tc =>
          val arguments = parseJson(tc.input).fold(throw _, identity)
          Step.FunctionCall(tc.id, tc.toolName, arguments)
        }
        outputStep ++ callSteps

      case ConversationEntry.ToolResult(toolCallId, toolName, result) =>
        List(Step.FunctionResult(callId = toolCallId, name = toolName, result = Json.fromString(result)))

      case ConversationEntry.IterationMarker(current, max) =>
        List(Step.userText(s"[Iteration $current of $max]"))
    }.toList

  override def sendRequest(
      history: ConversationHistory,
      backend: Backend[F],
      includeTools: Boolean
  ): F[AgentResponse] = {
    val request = InteractionRequest(
      model = modelName,
      input = InteractionInput.StepsInput(buildSteps(history)),
      systemInstruction = systemPrompt,
      tools = if (includeTools && convertedTools.nonEmpty) Some(convertedTools.toList) else None,
      responseFormat = responseFormat,
      store = Some(false)
    )

    monad.flatMap(monad.map(client.createInteraction(request).send(backend))(_.body)) {
      case Right(response) =>
        if (response.status == InteractionStatus.Failed)
          monad.error(new RuntimeException(s"Gemini interaction ${response.id.getOrElse("<unstored>")} failed"))
        else {
          val toolCalls = response.functionCalls.map(fc => ToolCall(fc.id, fc.name, fc.arguments.noSpaces))
          monad.unit(AgentResponse(response.outputText, toolCalls, mapStopReason(response, toolCalls.nonEmpty)))
        }

      case Left(error) =>
        monad.error(new RuntimeException(s"Gemini API error: ${error.getMessage}"))
    }
  }

  private def mapStopReason(response: InteractionResponse, hasToolCalls: Boolean): StopReason = {
    import sttp.ai.gemini.models.InteractionStatus._
    response.status match {
      case Completed if hasToolCalls => StopReason.ToolUse
      case Completed                 => StopReason.EndTurn
      case RequiresAction            => StopReason.ToolUse
      case Incomplete                => StopReason.MaxTokens
      case BudgetExceeded            => StopReason.MaxTokens
      case other                     => StopReason.Other(other.value)
    }
  }
}

object GeminiAgent {

  def builder[F[_]](
      geminiConfig: GeminiConfig,
      modelName: String
  )(implicit monad: sttp.monad.MonadError[F]): AgentBuilder[F] =
    builder(GeminiClient(geminiConfig), modelName)

  def builder[F[_]](
      client: GeminiClient,
      modelName: String
  )(implicit monad: sttp.monad.MonadError[F]): AgentBuilder[F] =
    AgentBuilder[F](config => new GeminiAgentBackend[F](client, modelName, config.userTools, config.systemPrompt, config.responseSchema))

  def synchronous(
      geminiConfig: GeminiConfig,
      modelName: String
  ): AgentBuilder[Identity] = builder[Identity](geminiConfig, modelName)(IdentityMonad)

  def synchronous(
      client: GeminiClient,
      modelName: String
  ): AgentBuilder[Identity] = builder[Identity](client, modelName)(IdentityMonad)
}
