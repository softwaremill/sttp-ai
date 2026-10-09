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

  override def createAgent(maxIterations: Int, tools: Seq[AgentTool[Identity, ?]]): Agent[Identity, String, String] = {
    val config = ClaudeConfig.fromEnv
    val client = ClaudeClient(config)
    val agentConfig = AgentConfig[Identity](maxIterations = maxIterations, userTools = tools)
    val agentBackend = new ClaudeAgentBackend[Identity](
      client,
      _ => sttp.ai.claude.models.ClaudeModel.ClaudeHaiku4_5,
      agentConfig.userTools,
      agentConfig.systemPrompt,
      agentConfig.responseSchema
    )(using IdentityMonad)
    Agent(agentBackend, agentConfig)(using IdentityMonad)
  }

  override def createTypedAgent[T](
      maxIterations: Int,
      tools: Seq[AgentTool[Identity, ?]],
      responseSchema: ResponseSchema[T]
  ): Agent[Identity, String, T] =
    ClaudeAgent
      .synchronous(ClaudeConfig.fromEnv, "claude-haiku-4-5-20251001")
      .maxIterations(maxIterations)
      .tools(tools)
      .responseSchema(responseSchema)
      .build
}
