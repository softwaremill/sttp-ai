package sttp.ai.openai.integration

import sttp.ai.core.agent.integration.AgentIntegrationSpecBase
import sttp.ai.core.agent._
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.monad.IdentityMonad
import sttp.shared.Identity

class OpenAIAgentIntegrationSpec extends AgentIntegrationSpecBase {

  override def providerName: String = "OpenAI"

  override def createAgent(maxIterations: Int, tools: Seq[AgentTool]): Agent[Identity] = {
    val openai = OpenAI.fromEnv
    val agentConfig = AgentConfig(maxIterations = maxIterations, userTools = tools).right.get
    val allTools = agentConfig.userTools ++ AgentConfig.systemTools
    val agentBackend = new OpenAIAgent[Identity](
      openai,
      "gpt-4o-mini",
      allTools,
      agentConfig.systemPrompt
    )(IdentityMonad)
    Agent(agentBackend, agentConfig)(IdentityMonad)
  }
}
