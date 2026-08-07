package sttp.ai.openai.integration

import sttp.ai.core.agent.integration.AgentIntegrationSpecBase
import sttp.ai.core.agent._
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent._
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.monad.IdentityMonad
import sttp.shared.Identity

class OpenAIAgentIntegrationSpec extends AgentIntegrationSpecBase {

  override def providerName: String = "OpenAI"
  override def apiKeyEnvVar: String = "OPENAI_API_KEY"

  override def createAgent(maxIterations: Int, tools: Seq[AgentTool[Identity, _]]): Agent[Identity, String, String] = {
    val openai = OpenAI.fromEnv
    val agentConfig = AgentConfig[Identity](maxIterations = maxIterations, userTools = tools)
    val agentBackend = new OpenAIAgentBackend[Identity](
      openai,
      _ => ChatCompletionModel.GPT4oMini,
      agentConfig.userTools,
      agentConfig.systemPrompt,
      agentConfig.responseSchema,
      strictTools = true
    )(IdentityMonad)
    Agent(agentBackend, agentConfig)(IdentityMonad)
  }

  override def createTypedAgent[T](
      maxIterations: Int,
      tools: Seq[AgentTool[Identity, _]],
      responseSchema: ResponseSchema[T]
  ): Agent[Identity, String, T] = {
    val openai = OpenAI.fromEnv
    OpenAIAgent
      .synchronous(openai, "gpt-4o-mini")
      .maxIterations(maxIterations)
      .tools(tools)
      .responseSchema(responseSchema)
      .build
  }
}
