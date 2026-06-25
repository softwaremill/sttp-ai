package sttp.ai.core.agent

import sttp.ai.core.agent.AgentConfig.SystemPromptParameters

case class AgentConfig[F[_]](
    maxIterations: Int = 10,
    systemPromptBuilder: Option[SystemPromptParameters => String] = Some(AgentConfig.buildSystemPrompt),
    userTools: Seq[AgentTool[F, _]] = Seq.empty[AgentTool[F, _]],
    exceptionHandler: ExceptionHandler = ExceptionHandler.default,
    responseSchema: Option[ResponseSchema[_]] = None,
    beforeToolCall: Option[ToolCall => F[Unit]] = None,
    afterToolCall: Option[ToolCallRecord => F[Unit]] = None
) {
  val systemPrompt: Option[String] = systemPromptBuilder.map(_.apply(SystemPromptParameters(maxIterations)))
}

object AgentConfig {

  case class SystemPromptParameters(maxIterations: Int)

  private def buildSystemPrompt(params: SystemPromptParameters): String =
    s"""You are a simple loop-based agent that solves the user's task step by step using tool calling when appropriate.
         |
         |OPERATING RULES:
         |1. The agent may run for a maximum of ${params.maxIterations} iterations.
         |2. In each iteration, decide whether to:
         |   - continue reasoning about the task and use one of the available tools to make progress,
         |   - or provide your final answer.
         |3. When the task is complete, respond with your final answer WITHOUT calling any tool. A response that contains no tool calls terminates the agent loop.
         |4. IF THIS IS THE LAST ALLOWED ITERATION, provide your final answer, even if the result is partial, approximate, or only a summary.
         |
         |GOAL: Solve the user's task as well as possible within the allowed number of iterations, making progress in each loop and ensuring proper termination.""".stripMargin
}
