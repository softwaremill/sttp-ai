package sttp.ai.core.agent

case class AgentConfig private (
    maxIterations: Int,
    systemPrompt: Option[String],
    userTools: Seq[AgentTool[_]],
    exceptionHandler: ExceptionHandler,
    responseSchema: Option[ResponseSchema[_]]
)

object AgentConfig {

  def apply(
      maxIterations: Int = 10,
      systemPrompt: Option[String] = None,
      userTools: Seq[AgentTool[_]] = Seq.empty,
      exceptionHandler: ExceptionHandler = ExceptionHandler.default,
      responseSchema: Option[ResponseSchema[_]] = None
  ): Either[String, AgentConfig] = {
    val finalSystemPrompt = systemPrompt.orElse(Some(buildSystemPrompt(maxIterations)))
    Right(new AgentConfig(maxIterations, finalSystemPrompt, userTools, exceptionHandler, responseSchema))
  }

  private def buildSystemPrompt(maxIterations: Int): String =
    s"""You are a simple loop-based agent that solves the user's task step by step using tool calling when appropriate.
         |
         |OPERATING RULES:
         |1. The agent may run for a maximum of $maxIterations iterations.
         |2. In each iteration, decide whether to:
         |   - continue reasoning about the task and use one of the available tools to make progress,
         |   - or provide your final answer.
         |3. When the task is complete, respond with your final answer WITHOUT calling any tool. A response that contains no tool calls terminates the agent loop.
         |4. IF THIS IS THE LAST ALLOWED ITERATION, provide your final answer, even if the result is partial, approximate, or only a summary.
         |
         |GOAL: Solve the user's task as well as possible within the allowed number of iterations, making progress in each loop and ensuring proper termination.""".stripMargin
}
