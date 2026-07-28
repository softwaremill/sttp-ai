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
      parameters = AgentTool.ensureObjectType(tool.rawJsonSchema)
    )

  /** Converts conversation history entries into replay steps.
    *
    * Note: `thought` steps returned by the API are not replayed — ConversationHistory has no representation for them. The live integration
    * suite's multi-iteration tool tests pass without echoing thought signatures, so the API currently accepts replays without them; if that
    * changes, carrying signatures would need support in core's ConversationHistory.
    *
    * Returns `Left` on the first tool-call argument that fails to parse as JSON, instead of throwing, so the failure surfaces through the
    * effect's error channel (`F`) rather than escaping it synchronously.
    */
  private def buildSteps(history: ConversationHistory): Either[Exception, List[Step]] = {
    def stepsFor(entry: ConversationEntry): Either[Exception, List[Step]] = entry match {
      case ConversationEntry.UserPrompt(content) =>
        Right(List(Step.userText(content)))

      case ConversationEntry.AssistantResponse(content, toolCalls) =>
        val outputStep = if (content.nonEmpty) List(Step.ModelOutput(List(Content.Text(content)))) else List.empty
        toolCalls
          .foldLeft[Either[Exception, List[Step]]](Right(List.empty)) { (acc, tc) =>
            for {
              steps <- acc
              arguments <- parseJson(tc.input)
            } yield steps :+ Step.FunctionCall(tc.id, tc.toolName, arguments)
          }
          .map(outputStep ++ _)

      case ConversationEntry.ToolResult(toolCallId, toolName, result) =>
        Right(List(Step.FunctionResult(callId = toolCallId, name = toolName, result = Json.fromString(result))))

      case ConversationEntry.IterationMarker(current, max) =>
        Right(List(Step.userText(s"[Iteration $current of $max]")))
    }

    history.entries.foldLeft[Either[Exception, List[Step]]](Right(List.empty)) { (acc, entry) =>
      for {
        steps <- acc
        next <- stepsFor(entry)
      } yield steps ++ next
    }
  }

  override def sendRequest(
      history: ConversationHistory,
      backend: Backend[F],
      includeTools: Boolean
  ): F[AgentResponse] =
    buildSteps(history) match {
      case Left(e)      => monad.error(e)
      case Right(steps) =>
        val request = InteractionRequest(
          model = modelName,
          input = InteractionInput.StepsInput(steps),
          systemInstruction = systemPrompt,
          tools = if (includeTools && convertedTools.nonEmpty) Some(convertedTools.toList) else None,
          responseFormat = responseFormat,
          store = Some(false)
        )

        monad.flatMap(monad.map(client.createInteraction(request).send(backend))(_.body)) {
          case Right(response) =>
            if (response.status == InteractionStatus.Failed || response.status == InteractionStatus.Cancelled)
              monad.error(
                new RuntimeException(
                  s"Gemini interaction ended with status '${response.status.value}'" +
                    s" (model: ${response.model.getOrElse("unknown")})" +
                    (if (response.outputText.nonEmpty) s": ${response.outputText}" else "")
                )
              )
            else {
              val toolCalls = response.functionCalls.map { fc =>
                val arguments = if (fc.arguments.isNull) Json.obj() else fc.arguments
                ToolCall(fc.id, fc.name, arguments.noSpaces)
              }
              monad.unit(AgentResponse(response.outputText, toolCalls, mapStopReason(response, toolCalls.nonEmpty)))
            }

          case Left(error) =>
            monad.error(error)
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
