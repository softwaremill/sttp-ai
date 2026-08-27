package sttp.ai.core.agent

import io.circe.Codec
import io.circe.Encoder
import io.circe.parser.decode
import sttp.ai.core.agent.AgentConfig.SystemPromptParameters
import sttp.ai.core.model.{AIModel, Capability, Supports}
import sttp.monad.MonadError
import sttp.tapir.Schema

/** Fluent agent configuration. `M` is the model (or family of models) the agent was created with; builder methods that need a model
  * capability require `Supports[M, C]` evidence, so e.g. adding tools to an agent whose model cannot call tools fails at compile time. `In`
  * and `Out` are the built agent's input and output types: a fresh builder starts at `String`/`String`; `inputRenderer` and `input`
  * transition `In`, `responseSchema`/`deriveResponseSchema` transition `Out`.
  */
final class AgentBuilder[F[_], M <: AIModel, In, Out] private (
    makeBackend: AgentConfig[F] => AgentBackend[F],
    val config: AgentConfig[F],
    renderInput: In => String,
    parseOutput: String => Either[io.circe.Error, Out]
)(implicit monad: MonadError[F]) {

  private def withConfig(next: AgentConfig[F]): AgentBuilder[F, M, In, Out] =
    new AgentBuilder[F, M, In, Out](makeBackend, next, renderInput, parseOutput)

  def maxIterations(value: Int): AgentBuilder[F, M, In, Out] = withConfig(config.copy(maxIterations = value))

  /** Caps the tokens the model may generate per LLM call. Unset, each provider's default applies (Claude/Gemini: 4096, OpenAI: no cap). */
  def maxTokens(value: Int): AgentBuilder[F, M, In, Out] = withConfig(config.copy(maxTokens = Some(value)))

  def systemPrompt(buildSystemPrompt: SystemPromptParameters => String): AgentBuilder[F, M, In, Out] =
    withConfig(config.copy(systemPromptBuilder = Some(buildSystemPrompt)))

  def systemPrompt(prompt: String): AgentBuilder[F, M, In, Out] = systemPrompt(_ => prompt)

  def tools(values: Seq[AgentTool[F, _]])(implicit ev: Supports[M, Capability.ToolCalling]): AgentBuilder[F, M, In, Out] =
    withConfig(config.copy(userTools = values))

  def tools(first: AgentTool[F, _], rest: AgentTool[F, _]*)(implicit
      ev: Supports[M, Capability.ToolCalling]
  ): AgentBuilder[F, M, In, Out] =
    tools(first +: rest)

  def addTool(tool: AgentTool[F, _])(implicit ev: Supports[M, Capability.ToolCalling]): AgentBuilder[F, M, In, Out] =
    withConfig(config.copy(userTools = config.userTools :+ tool))

  def exceptionHandler(handler: ExceptionHandler): AgentBuilder[F, M, In, Out] = withConfig(config.copy(exceptionHandler = handler))

  /** Types the agent's input: `In2` is rendered into the initial user message with the given function. */
  def inputRenderer[In2](render: In2 => String): AgentBuilder[F, M, In2, Out] =
    new AgentBuilder[F, M, In2, Out](makeBackend, config, render, parseOutput)

  /** Types the agent's input: `In2` is rendered into the initial user message as compact JSON via its circe `Encoder`, wrapped in a small
    * fixed envelope — the message is exactly `"Process the following input data (JSON):"`, a blank line, and the `noSpaces` JSON (useful
    * when asserting on prompts in tests). Use [[inputRenderer]] to control the rendering explicitly. Note that `input[String]` would
    * JSON-quote the value (`"..."`) inside the envelope — for plain string prompts keep the default `String` input, which renders identity,
    * or use [[inputRenderer]].
    */
  def input[In2](implicit enc: Encoder[In2]): AgentBuilder[F, M, In2, Out] =
    inputRenderer[In2](in => AgentBuilder.renderJsonInput(enc(in)))

  /** Types the agent's output: the final answer is requested as structured output matching the schema and parsed with its codec. */
  def responseSchema[T](schema: ResponseSchema[T])(implicit ev: Supports[M, Capability.StructuredOutput]): AgentBuilder[F, M, In, T] =
    new AgentBuilder[F, M, In, T](
      makeBackend,
      config.copy(responseSchema = Some(schema)),
      renderInput,
      answer => decode[T](answer)(schema.codec)
    )

  def deriveResponseSchema[T](implicit
      schema: Schema[T],
      codec: Codec[T],
      ev: Supports[M, Capability.StructuredOutput]
  ): AgentBuilder[F, M, In, T] =
    responseSchema(ResponseSchema.derived[T](None))

  // NOTE: the description field is only forwarded to OpenAI. Claude's structured-output `output_config` has no description field.
  def deriveResponseSchema[T](description: String)(implicit
      schema: Schema[T],
      codec: Codec[T],
      ev: Supports[M, Capability.StructuredOutput]
  ): AgentBuilder[F, M, In, T] =
    responseSchema(ResponseSchema.derived[T](Some(description)))

  /** Appends an interceptor. Interceptors wrap stages in list order: the first added is outermost. */
  def addInterceptor(value: AgentInterceptor[F]): AgentBuilder[F, M, In, Out] =
    withConfig(config.copy(interceptors = config.interceptors :+ value))

  /** Replaces the interceptor list. */
  def interceptors(values: Seq[AgentInterceptor[F]]): AgentBuilder[F, M, In, Out] =
    withConfig(config.copy(interceptors = values))

  def build: Agent[F, In, Out] = new LoopAgent[F, In, Out](makeBackend(config), config, renderInput, parseOutput)
}

object AgentBuilder {

  def apply[F[_], M <: AIModel](
      makeBackend: AgentConfig[F] => AgentBackend[F]
  )(implicit monad: MonadError[F]): AgentBuilder[F, M, String, String] =
    new AgentBuilder[F, M, String, String](makeBackend, AgentConfig[F](), identity, answer => Right(answer))

  private[agent] def renderJsonInput(json: io.circe.Json): String =
    s"""Process the following input data (JSON):
       |
       |${json.noSpaces}""".stripMargin
}
