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
    modelForIteration: IterationInfo => ChatCompletionModel,
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
      backend: Backend[F],
      includeTools: Boolean,
      iterationInfo: IterationInfo
  ): F[AgentResponse] = {
    val messages = buildMessages(history)
    val request = ChatBody(
      model = modelForIteration(iterationInfo),
      messages = messages,
      tools = if (includeTools && convertedTools.nonEmpty) Some(convertedTools) else None,
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

        val usage = TokenUsage(
          inputTokens = Tokens(response.usage.promptTokens.toLong),
          outputTokens = Tokens(response.usage.completionTokens.toLong),
          cachedInputTokens = Tokens(response.usage.promptTokensDetails.flatMap(_.cachedTokens).getOrElse(0).toLong),
          reasoningTokens = Tokens(response.usage.completionTokensDetails.flatMap(_.reasoningTokens).getOrElse(0).toLong)
        )

        monad.unit(AgentResponse(textContent, toolCalls, stopReason, usage = Some(usage), model = Some(response.model)))

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

  /** Entry point: `OpenAIAgent.builder[F](openAI, model)`. The indirection lets `M` be inferred while `F` is given explicitly. */
  def builder[F[_]]: BuilderPartiallyApplied[F] = new BuilderPartiallyApplied[F]

  final class BuilderPartiallyApplied[F[_]] private[OpenAIAgent] () {

    def apply[M <: ChatCompletionModel](openAI: OpenAI, model: M)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, M, String, String] =
      apply(openAI, model, strictTools = true)

    def apply[M <: ChatCompletionModel](openAI: OpenAI, model: M, strictTools: Boolean)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, M, String, String] =
      apply(openAI, (_: IterationInfo) => model, strictTools)

    /** Selects the model per loop iteration (e.g. a cheap model for tool iterations, a stronger one for the forced-final synthesis). `M`
      * infers to the least upper bound of every model the function can return, so capability checks require the shared capabilities.
      */
    def apply[M <: ChatCompletionModel](openAI: OpenAI, modelForIteration: IterationInfo => M)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, M, String, String] =
      apply(openAI, modelForIteration, strictTools = true)

    def apply[M <: ChatCompletionModel](openAI: OpenAI, modelForIteration: IterationInfo => M, strictTools: Boolean)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, M, String, String] =
      AgentBuilder[F, M](config =>
        new OpenAIAgentBackend[F](openAI, modelForIteration, config.userTools, config.systemPrompt, config.responseSchema, strictTools)
      )

    def apply(openAI: OpenAI, modelName: String)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, ChatCompletionModel.CustomChatCompletionModel, String, String] =
      apply(openAI, ChatCompletionModel.CustomChatCompletionModel(modelName))

    def apply(openAI: OpenAI, modelName: String, strictTools: Boolean)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, ChatCompletionModel.CustomChatCompletionModel, String, String] =
      apply(openAI, ChatCompletionModel.CustomChatCompletionModel(modelName), strictTools)

    def apply(apiKey: String, modelName: String)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, ChatCompletionModel.CustomChatCompletionModel, String, String] =
      apply(new OpenAI(apiKey), modelName)

    def apply(apiKey: String, modelName: String, strictTools: Boolean)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, ChatCompletionModel.CustomChatCompletionModel, String, String] =
      apply(new OpenAI(apiKey), modelName, strictTools)

    def apply[M <: ChatCompletionModel](apiKey: String, model: M)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, M, String, String] =
      apply(new OpenAI(apiKey), model)

    def apply[M <: ChatCompletionModel](apiKey: String, model: M, strictTools: Boolean)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, M, String, String] =
      apply(new OpenAI(apiKey), model, strictTools)

    def apply[M <: ChatCompletionModel](apiKey: String, modelForIteration: IterationInfo => M)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, M, String, String] =
      apply(new OpenAI(apiKey), modelForIteration)

    def apply[M <: ChatCompletionModel](apiKey: String, modelForIteration: IterationInfo => M, strictTools: Boolean)(implicit
        monad: sttp.monad.MonadError[F]
    ): AgentBuilder[F, M, String, String] =
      apply(new OpenAI(apiKey), modelForIteration, strictTools)
  }

  def synchronous[M <: ChatCompletionModel](openAI: OpenAI, model: M): AgentBuilder[Identity, M, String, String] =
    builder[Identity](openAI, model)(IdentityMonad)

  def synchronous[M <: ChatCompletionModel](openAI: OpenAI, model: M, strictTools: Boolean): AgentBuilder[Identity, M, String, String] =
    builder[Identity](openAI, model, strictTools)(IdentityMonad)

  def synchronous[M <: ChatCompletionModel](
      openAI: OpenAI,
      modelForIteration: IterationInfo => M
  ): AgentBuilder[Identity, M, String, String] =
    builder[Identity](openAI, modelForIteration)(IdentityMonad)

  def synchronous[M <: ChatCompletionModel](
      openAI: OpenAI,
      modelForIteration: IterationInfo => M,
      strictTools: Boolean
  ): AgentBuilder[Identity, M, String, String] =
    builder[Identity](openAI, modelForIteration, strictTools)(IdentityMonad)

  def synchronous(
      openAI: OpenAI,
      modelName: String
  ): AgentBuilder[Identity, ChatCompletionModel.CustomChatCompletionModel, String, String] =
    builder[Identity](openAI, modelName)(IdentityMonad)

  def synchronous(
      openAI: OpenAI,
      modelName: String,
      strictTools: Boolean
  ): AgentBuilder[Identity, ChatCompletionModel.CustomChatCompletionModel, String, String] =
    builder[Identity](openAI, modelName, strictTools)(IdentityMonad)

  def synchronous(
      apiKey: String,
      modelName: String
  ): AgentBuilder[Identity, ChatCompletionModel.CustomChatCompletionModel, String, String] =
    builder[Identity](apiKey, modelName)(IdentityMonad)

  def synchronous(
      apiKey: String,
      modelName: String,
      strictTools: Boolean
  ): AgentBuilder[Identity, ChatCompletionModel.CustomChatCompletionModel, String, String] =
    builder[Identity](apiKey, modelName, strictTools)(IdentityMonad)

  def synchronous[M <: ChatCompletionModel](apiKey: String, model: M): AgentBuilder[Identity, M, String, String] =
    builder[Identity](apiKey, model)(IdentityMonad)

  def synchronous[M <: ChatCompletionModel](apiKey: String, model: M, strictTools: Boolean): AgentBuilder[Identity, M, String, String] =
    builder[Identity](apiKey, model, strictTools)(IdentityMonad)

  def synchronous[M <: ChatCompletionModel](
      apiKey: String,
      modelForIteration: IterationInfo => M
  ): AgentBuilder[Identity, M, String, String] =
    builder[Identity](apiKey, modelForIteration)(IdentityMonad)

  def synchronous[M <: ChatCompletionModel](
      apiKey: String,
      modelForIteration: IterationInfo => M,
      strictTools: Boolean
  ): AgentBuilder[Identity, M, String, String] =
    builder[Identity](apiKey, modelForIteration, strictTools)(IdentityMonad)
}
