package sttp.ai.openai.integration

import sttp.ai.core.agent.integration.AgentIntegrationSpecBase
import sttp.ai.core.agent._
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent._
import sttp.monad.IdentityMonad
import sttp.shared.Identity

class OpenAIAgentIntegrationSpec extends AgentIntegrationSpecBase {

  override def providerName: String = "OpenAI"
  override def apiKeyEnvVar: String = "OPENAI_API_KEY"

  override def createAgent(maxIterations: Int, tools: Seq[AgentTool[_]]): Agent[Identity] = {
    val openai = OpenAI.fromEnv
    val agentConfig = AgentConfig[Identity](maxIterations = maxIterations, userTools = tools)
    val agentBackend = new OpenAIAgentBackend[Identity](
      openai,
      "gpt-4o-mini",
      agentConfig.userTools,
      agentConfig.systemPrompt,
      agentConfig.responseSchema
    )(IdentityMonad)
    Agent(agentBackend, agentConfig)(IdentityMonad)
  }

  override def createTypedAgent[T](
      maxIterations: Int,
      tools: Seq[AgentTool[_]],
      responseSchema: ResponseSchema[T]
  ): Agent[Identity] = {
    val openai = OpenAI.fromEnv
    OpenAIAgent
      .synchronous(openai, "gpt-4o-mini")
      .maxIterations(maxIterations)
      .tools(tools)
      .responseSchema(responseSchema)
      .build
  }
}
