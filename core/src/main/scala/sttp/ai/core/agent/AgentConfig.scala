package sttp.ai.core.agent

case class AgentConfig private (
    maxIterations: Int,
    systemPrompt: Option[String],
    userTools: Seq[AgentTool]
)

object AgentConfig {
  private[ai] val systemTools: Seq[AgentTool] = Seq(new FinishTool())
  private val reservedToolNames: Set[String] = Set(FinishTool.ToolName)

  def apply(
      maxIterations: Int = 10,
      systemPrompt: Option[String] = None,
      userTools: Seq[AgentTool] = Seq.empty
  ): Either[String, AgentConfig] = {
    val conflictingTools = userTools.filter(t => reservedToolNames.contains(t.name))
    if (conflictingTools.isEmpty) {
      Right(new AgentConfig(maxIterations, systemPrompt, userTools))
    } else {
      Left(
        s"Cannot provide tools with reserved names: ${reservedToolNames.mkString(", ")}. " +
          s"The following tools conflict: ${conflictingTools.map(_.name).mkString(", ")}. " +
          s"These are system tools managed internally."
      )
    }
  }

  def default: Either[String, AgentConfig] = apply(
    systemPrompt = Some(buildSystemPrompt(10))
  )

  private def buildSystemPrompt(maxIterations: Int): String =
    s"""You are a simple loop-based agent that solves the user's task step by step using tool calling when appropriate.
       |
       |Among the available tools, there is ONE SPECIAL tool named finish. The finish tool is used exclusively to explicitly terminate the agent's execution and stop the loop on the system side. It has higher priority than all other tools.
       |
       |OPERATING RULES:
       |1. The agent may run for a maximum of $maxIterations iterations.
       |2. In each iteration, decide whether to:
       |   - continue reasoning about the task,
       |   - use one of the available tools,
       |   - or terminate the agent.
       |3. If the task is completed before reaching the iteration limit, IMMEDIATELY call the special finish tool.
       |4. IF THIS IS THE LAST ALLOWED ITERATION, you MUST call the special finish tool, even if the result is partial, approximate, or only a summary.
       |5. The agent must not exceed the iteration limit without calling finish.
       |
       |GOAL: Solve the user's task as well as possible within the allowed number of iterations, making progress in each loop and ensuring proper termination.""".stripMargin
}
