package sttp.ai.claude.integration

import sttp.ai.core.agent.integration.AgentIntegrationSpecBase
import sttp.ai.core.agent._
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.agent._
import sttp.ai.claude.config.ClaudeConfig
import sttp.monad.IdentityMonad
import sttp.shared.Identity

class ClaudeAgentIntegrationSpec extends AgentIntegrationSpecBase {

  override def providerName: String = "Claude"
  override def apiKeyEnvVar: String = "ANTHROPIC_API_KEY"

  override def createAgent(maxIterations: Int, tools: Seq[AgentTool[_]]): Agent[Identity] = {
    val config = ClaudeConfig.fromEnv
    val client = ClaudeClient(config)
    val agentConfig = AgentConfig(maxIterations = maxIterations, userTools = tools).right.get
    val allTools = agentConfig.userTools ++ AgentConfig.systemTools(agentConfig)
    val agentBackend = new ClaudeAgentBackend[Identity](
      client,
      "claude-haiku-4-5-20251001",
      allTools,
      agentConfig.systemPrompt
    )(IdentityMonad)
    Agent(agentBackend, agentConfig)(IdentityMonad)
  }

  override def createTypedAgent[T](
      maxIterations: Int,
      tools: Seq[AgentTool[_]],
      responseSchema: ResponseSchema[T]
  ): Agent[Identity] = {
    val agentConfig = AgentConfig(
      maxIterations = maxIterations,
      userTools = tools,
      responseSchema = Some(responseSchema)
    ).right.get
    ClaudeAgent.synchronous(ClaudeConfig.fromEnv, "claude-haiku-4-5-20251001", agentConfig)
  }
}
