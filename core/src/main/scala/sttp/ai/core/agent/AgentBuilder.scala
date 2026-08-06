package sttp.ai.core.agent

import io.circe.Codec
import sttp.ai.core.agent.AgentConfig.SystemPromptParameters
import sttp.ai.core.model.{AIModel, Capability, Supports}
import sttp.monad.MonadError
import sttp.tapir.Schema

/** Fluent agent configuration. `M` is the model (or family of models) the agent was created with; builder methods that need a model
  * capability require `Supports[M, C]` evidence, so e.g. adding tools to an agent whose model cannot call tools fails at compile time.
  */
final class AgentBuilder[F[_], M <: AIModel] private (
    makeBackend: AgentConfig[F] => AgentBackend[F],
    val config: AgentConfig[F]
)(implicit monad: MonadError[F]) {

  private def withConfig(next: AgentConfig[F]): AgentBuilder[F, M] =
    new AgentBuilder[F, M](makeBackend, next)

  def maxIterations(value: Int): AgentBuilder[F, M] = withConfig(config.copy(maxIterations = value))

  def systemPrompt(buildSystemPrompt: SystemPromptParameters => String): AgentBuilder[F, M] =
    withConfig(config.copy(systemPromptBuilder = Some(buildSystemPrompt)))

  def systemPrompt(prompt: String): AgentBuilder[F, M] = systemPrompt(_ => prompt)

  def tools(values: Seq[AgentTool[F, _]])(implicit ev: Supports[M, Capability.ToolCalling]): AgentBuilder[F, M] =
    withConfig(config.copy(userTools = values))

  def tools(first: AgentTool[F, _], rest: AgentTool[F, _]*)(implicit ev: Supports[M, Capability.ToolCalling]): AgentBuilder[F, M] =
    tools(first +: rest)

  def addTool(tool: AgentTool[F, _])(implicit ev: Supports[M, Capability.ToolCalling]): AgentBuilder[F, M] =
    withConfig(config.copy(userTools = config.userTools :+ tool))

  def exceptionHandler(handler: ExceptionHandler): AgentBuilder[F, M] = withConfig(config.copy(exceptionHandler = handler))

  def responseSchema(schema: ResponseSchema[_])(implicit ev: Supports[M, Capability.StructuredOutput]): AgentBuilder[F, M] =
    withConfig(config.copy(responseSchema = Some(schema)))

  def deriveResponseSchema[T](implicit
      schema: Schema[T],
      codec: Codec[T],
      ev: Supports[M, Capability.StructuredOutput]
  ): AgentBuilder[F, M] =
    withConfig(config.copy(responseSchema = Some(ResponseSchema.derived[T](None))))

  // NOTE: the description field is only forwarded to OpenAI. Claude's structured-output `output_config` has no description field.
  def deriveResponseSchema[T](description: String)(implicit
      schema: Schema[T],
      codec: Codec[T],
      ev: Supports[M, Capability.StructuredOutput]
  ): AgentBuilder[F, M] =
    withConfig(config.copy(responseSchema = Some(ResponseSchema.derived[T](Some(description)))))

  @deprecated("Use interceptor(...) with an AgentInterceptor overriding aroundToolCall", "0.8.0")
  def hookBeforeToolCall(hook: ToolCall => F[Unit]): AgentBuilder[F, M] = withConfig(config.copy(beforeToolCall = Some(hook)))

  @deprecated("Use interceptor(...) with an AgentInterceptor overriding aroundToolCall", "0.8.0")
  def hookAfterToolCall(hook: ToolCallRecord => F[Unit]): AgentBuilder[F, M] = withConfig(config.copy(afterToolCall = Some(hook)))

  /** Appends an interceptor. Interceptors wrap stages in list order: the first added is outermost. */
  def interceptor(value: AgentInterceptor[F]): AgentBuilder[F, M] =
    withConfig(config.copy(interceptors = config.interceptors :+ value))

  /** Replaces the interceptor list. */
  def interceptors(values: Seq[AgentInterceptor[F]]): AgentBuilder[F, M] =
    withConfig(config.copy(interceptors = values))

  def build: Agent[F] = Agent(makeBackend(config), config)
}

object AgentBuilder {

  def apply[F[_], M <: AIModel](makeBackend: AgentConfig[F] => AgentBackend[F])(implicit monad: MonadError[F]): AgentBuilder[F, M] =
    new AgentBuilder[F, M](makeBackend, AgentConfig[F]())
}
