package sttp.ai.openai.integration

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.ai.core.agent.integration.AgentIntegrationSpecBase
import sttp.ai.core.agent._
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent._
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.client4.DefaultSyncBackend
import sttp.monad.IdentityMonad
import sttp.shared.Identity
import sttp.tapir.Schema

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

  case class CityReport(city: String, temperatureSummary: String)
  object CityReport {
    implicit val codec: Codec[CityReport] = deriveCodec
    implicit val schema: Schema[CityReport] = Schema.derived
  }

  it should "hand off a typed result between two composed agents" in {
    if (maybeApiKey.isEmpty) cancel(s"$apiKeyEnvVar not defined - skipping integration test")
    val backend = DefaultSyncBackend()
    try {
      val openai = OpenAI.fromEnv
      val reporter = OpenAIAgent
        .synchronous(openai, "gpt-4o-mini")
        .maxIterations(3)
        .tools(weatherTool)
        .deriveResponseSchema[CityReport]
        .build
      val summarizer = OpenAIAgent
        .synchronous(openai, "gpt-4o-mini")
        .maxIterations(2)
        .inputRenderer[CityReport](r => s"In five words or fewer, restate: ${r.temperatureSummary}")
        .build

      val result = reporter
        .andThen(summarizer)
        .run("What's the weather in Paris? Reply with the city and a temperature summary.")(backend)

      result.finalAnswer.isRight shouldBe true: Unit
      result.llmCalls.size should be >= 2
    } finally backend.close()
  }
}
