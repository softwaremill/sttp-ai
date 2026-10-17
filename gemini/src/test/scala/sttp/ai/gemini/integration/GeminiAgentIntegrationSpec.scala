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

  override def createAgent(maxIterations: Int, tools: Seq[AgentTool[Identity, ?]]): Agent[Identity, String, String] = {
    val config = GeminiConfig.fromEnv
    val client = GeminiClient(config)
    val agentConfig = AgentConfig[Identity](maxIterations = maxIterations, userTools = tools)
    val agentBackend = new GeminiAgentBackend[Identity](
      client,
      _ => GeminiModel.Gemini35FlashLite,
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
    GeminiAgent
      .synchronous(GeminiConfig.fromEnv, GeminiModel.Gemini35FlashLite)
      .maxIterations(maxIterations)
      .tools(tools)
      .responseSchema(responseSchema)
      .build
}
