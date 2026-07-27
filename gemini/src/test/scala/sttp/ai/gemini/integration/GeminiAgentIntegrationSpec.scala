package sttp.ai.gemini.integration

import sttp.ai.core.agent.integration.AgentIntegrationSpecBase
import sttp.ai.core.agent._
import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.agent._
import sttp.ai.gemini.config.GeminiConfig
import sttp.monad.IdentityMonad
import sttp.shared.Identity

class GeminiAgentIntegrationSpec extends AgentIntegrationSpecBase {

  private val testModel = "gemini-2.5-flash-lite"

  override def providerName: String = "Gemini"
  override def apiKeyEnvVar: String = "GEMINI_API_KEY"

  override def createAgent(maxIterations: Int, tools: Seq[AgentTool[Identity, _]]): Agent[Identity] = {
    val config = GeminiConfig.fromEnv
    val client = GeminiClient(config)
    val agentConfig = AgentConfig[Identity](maxIterations = maxIterations, userTools = tools)
    val agentBackend = new GeminiAgentBackend[Identity](
      client,
      testModel,
      agentConfig.userTools,
      agentConfig.systemPrompt,
      agentConfig.responseSchema
    )(IdentityMonad)
    Agent(agentBackend, agentConfig)(IdentityMonad)
  }

  override def createTypedAgent[T](
      maxIterations: Int,
      tools: Seq[AgentTool[Identity, _]],
      responseSchema: ResponseSchema[T]
  ): Agent[Identity] =
    GeminiAgent
      .synchronous(GeminiConfig.fromEnv, testModel)
      .maxIterations(maxIterations)
      .tools(tools)
      .responseSchema(responseSchema)
      .build
}
