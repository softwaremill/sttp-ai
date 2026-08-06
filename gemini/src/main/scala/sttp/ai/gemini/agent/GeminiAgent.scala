package sttp.ai.gemini.agent

import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.models.{Content, GeminiModel, GenerationConfig, InteractionInput, InteractionStatus, ResponseFormat, Step, Tool}
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
    modelForIteration: IterationInfo => GeminiModel,
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
      includeTools: Boolean,
      iterationInfo: IterationInfo
  ): F[AgentResponse] =
    buildSteps(history) match {
      case Left(e)      => monad.error(e)
      case Right(steps) =>
        val request = InteractionRequest(
          model = modelForIteration(iterationInfo).value,
          input = InteractionInput.StepsInput(steps),
          systemInstruction = systemPrompt,
          tools = if (includeTools && convertedTools.nonEmpty) Some(convertedTools.toList) else None,
          responseFormat = responseFormat,
          store = Some(false),
          generationConfig = Some(GenerationConfig(maxOutputTokens = Some(4096)))
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
              val usage = response.usage.map { u =>
                TokenUsage(
                  inputTokens = Tokens(u.totalInputTokens.getOrElse(0L)),
                  outputTokens = Tokens(u.totalOutputTokens.getOrElse(0L)),
                  cachedInputTokens = Tokens(u.totalCachedTokens.getOrElse(0L)),
                  reasoningTokens = Tokens(u.totalThoughtTokens.getOrElse(0L))
                )
              }
              monad.unit(
                AgentResponse(
                  response.outputText,
                  toolCalls,
                  mapStopReason(response, toolCalls.nonEmpty),
                  usage = usage,
                  model = response.model
                )
              )
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

  /** Entry point: `GeminiAgent.builder[F](client, model)`. The indirection lets `M` be inferred while `F` is given explicitly. */
  def builder[F[_]]: BuilderPartiallyApplied[F] = new BuilderPartiallyApplied[F]

  final class BuilderPartiallyApplied[F[_]] private[GeminiAgent] () {

    def apply[M <: GeminiModel](client: GeminiClient, model: M)(implicit monad: sttp.monad.MonadError[F]): AgentBuilder[F, M] =
      apply(client, (_: IterationInfo) => model)

    /** Selects the model per loop iteration (e.g. a cheap model for tool iterations, a stronger one for the forced-final synthesis). `M`
      * infers to the least upper bound of every model the function can return, so capability checks require the shared capabilities.
      */
    def apply[M <: GeminiModel](client: GeminiClient, modelForIteration: IterationInfo => M)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, M] =
      AgentBuilder[F, M](config =>
        new GeminiAgentBackend[F](client, modelForIteration, config.userTools, config.systemPrompt, config.responseSchema)
      )

    def apply(client: GeminiClient, modelName: String)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, GeminiModel.CustomModel] =
      apply(client, GeminiModel.CustomModel(modelName))

    def apply[M <: GeminiModel](geminiConfig: GeminiConfig, model: M)(implicit monad: sttp.monad.MonadError[F]): AgentBuilder[F, M] =
      apply(GeminiClient(geminiConfig), model)

    def apply[M <: GeminiModel](geminiConfig: GeminiConfig, modelForIteration: IterationInfo => M)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, M] =
      apply(GeminiClient(geminiConfig), modelForIteration)

    def apply(geminiConfig: GeminiConfig, modelName: String)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, GeminiModel.CustomModel] =
      apply(GeminiClient(geminiConfig), modelName)
  }

  def synchronous[M <: GeminiModel](client: GeminiClient, model: M): AgentBuilder[Identity, M] =
    builder[Identity](client, model)(IdentityMonad)

  def synchronous[M <: GeminiModel](client: GeminiClient, modelForIteration: IterationInfo => M): AgentBuilder[Identity, M] =
    builder[Identity](client, modelForIteration)(IdentityMonad)

  def synchronous(client: GeminiClient, modelName: String): AgentBuilder[Identity, GeminiModel.CustomModel] =
    builder[Identity](client, modelName)(IdentityMonad)

  def synchronous[M <: GeminiModel](geminiConfig: GeminiConfig, model: M): AgentBuilder[Identity, M] =
    builder[Identity](geminiConfig, model)(IdentityMonad)

  def synchronous[M <: GeminiModel](geminiConfig: GeminiConfig, modelForIteration: IterationInfo => M): AgentBuilder[Identity, M] =
    builder[Identity](geminiConfig, modelForIteration)(IdentityMonad)

  def synchronous(geminiConfig: GeminiConfig, modelName: String): AgentBuilder[Identity, GeminiModel.CustomModel] =
    builder[Identity](geminiConfig, modelName)(IdentityMonad)
}
