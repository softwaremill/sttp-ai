package sttp.ai.core.agent

import io.circe.Codec
import sttp.ai.core.agent.AgentConfig.SystemPromptParameters
import sttp.monad.MonadError
import sttp.tapir.Schema

final class AgentBuilder[F[_]] private (
    makeBackend: AgentConfig[F] => AgentBackend[F],
    val config: AgentConfig[F]
)(implicit monad: MonadError[F]) {

  private def withConfig(next: AgentConfig[F]): AgentBuilder[F] =
    new AgentBuilder[F](makeBackend, next)

  def maxIterations(value: Int): AgentBuilder[F] = withConfig(config.copy(maxIterations = value))

  def systemPrompt(buildSystemPrompt: SystemPromptParameters => String): AgentBuilder[F] =
    withConfig(config.copy(systemPromptBuilder = Some(buildSystemPrompt)))

  def systemPrompt(prompt: String): AgentBuilder[F] = systemPrompt(_ => prompt)

  def tools(values: Seq[AgentTool[F, _]]): AgentBuilder[F] = withConfig(config.copy(userTools = values))

  def tools(first: AgentTool[F, _], rest: AgentTool[F, _]*): AgentBuilder[F] = tools(first +: rest)

  def addTool(tool: AgentTool[F, _]): AgentBuilder[F] = withConfig(config.copy(userTools = config.userTools :+ tool))

  def exceptionHandler(handler: ExceptionHandler): AgentBuilder[F] = withConfig(config.copy(exceptionHandler = handler))

  def responseSchema(schema: ResponseSchema[_]): AgentBuilder[F] = withConfig(config.copy(responseSchema = Some(schema)))

  def deriveResponseSchema[T](implicit schema: Schema[T], codec: Codec[T]): AgentBuilder[F] =
    withConfig(config.copy(responseSchema = Some(ResponseSchema.derived[T](None))))

  // NOTE: the description field is only forwarded to OpenAI. Claude's structured-output `output_config` has no description field.
  def deriveResponseSchema[T](description: String)(implicit schema: Schema[T], codec: Codec[T]): AgentBuilder[F] =
    withConfig(config.copy(responseSchema = Some(ResponseSchema.derived[T](Some(description)))))

  def hookBeforeToolCall(hook: ToolCall => F[Unit]): AgentBuilder[F] = withConfig(config.copy(beforeToolCall = Some(hook)))

  def hookAfterToolCall(hook: ToolCallRecord => F[Unit]): AgentBuilder[F] = withConfig(config.copy(afterToolCall = Some(hook)))

  def build: Agent[F] = Agent(makeBackend(config), config)
}

object AgentBuilder {

  def apply[F[_]](makeBackend: AgentConfig[F] => AgentBackend[F])(implicit monad: MonadError[F]): AgentBuilder[F] =
    new AgentBuilder[F](makeBackend, AgentConfig[F]())
}
