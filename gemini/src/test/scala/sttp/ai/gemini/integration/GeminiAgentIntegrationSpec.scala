package sttp.ai.gemini.integration

import sttp.ai.core.agent.integration.AgentIntegrationSpecBase
import sttp.ai.core.agent._
import sttp.ai.gemini.GeminiClient
import sttp.ai.gemini.agent._
import sttp.ai.gemini.config.GeminiConfig
import sttp.ai.gemini.models.GeminiModel
import sttp.monad.IdentityMonad
import sttp.shared.Identity

class GeminiAgentIntegrationSpec extends AgentIntegrationSpecBase {

  override def providerName: String = "Gemini"
  override def apiKeyEnvVar: String = "GEMINI_API_KEY"

  override def createAgent(maxIterations: Int, tools: Seq[AgentTool[Identity, _]]): Agent[Identity] = {
    val config = GeminiConfig.fromEnv
    val client = GeminiClient(config)
    val agentConfig = AgentConfig[Identity](maxIterations = maxIterations, userTools = tools)
    val agentBackend = new GeminiAgentBackend[Identity](
      client,
      _ => GeminiModel.Gemini35FlashLite,
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
      .synchronous(GeminiConfig.fromEnv, GeminiModel.Gemini35FlashLite.value)
      .maxIterations(maxIterations)
      .tools(tools)
      .responseSchema(responseSchema)
      .build
}
