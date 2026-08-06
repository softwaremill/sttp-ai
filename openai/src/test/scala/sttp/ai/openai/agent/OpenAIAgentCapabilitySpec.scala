package sttp.ai.openai.agent

import io.circe.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.OpenAI
import sttp.ai.core.agent.{AgentTool, IterationInfo}
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.apispec.Schema
import sttp.shared.Identity

object OpenAIAgentCapabilitySpecFixtures {
  val openAI: OpenAI = new OpenAI("test-key")
  val echoTool: AgentTool[Identity, _] = {
    val schema = parse("""{"type":"object"}""").toOption.get.as[Schema](sttp.apispec.circe.schemaDecoder).toOption.get
    AgentTool.dynamic("echo", "Echoes input", schema)(_ => "ok")
  }
}

class OpenAIAgentCapabilitySpec extends AnyFlatSpec with Matchers {
  import OpenAIAgentCapabilitySpecFixtures._

  "OpenAIAgent" should "accept tools for a ToolCalling model" in {
    OpenAIAgent.synchronous(openAI, ChatCompletionModel.GPT4o).tools(echoTool): Unit
    succeed
  }

  it should "reject tools for o1-mini (no ToolCalling) at compile time" in {
    assertDoesNotCompile(
      """sttp.ai.openai.agent.OpenAIAgent
        .synchronous(
          sttp.ai.openai.agent.OpenAIAgentCapabilitySpecFixtures.openAI,
          sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel.O1Mini
        )
        .tools(sttp.ai.openai.agent.OpenAIAgentCapabilitySpecFixtures.echoTool)"""
    )
    assertCompiles(
      """sttp.ai.openai.agent.OpenAIAgent
        .synchronous(
          sttp.ai.openai.agent.OpenAIAgentCapabilitySpecFixtures.openAI,
          sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel.GPT4o
        )
        .tools(sttp.ai.openai.agent.OpenAIAgentCapabilitySpecFixtures.echoTool)"""
    )
  }

  it should "keep String model names working (custom model claims all capabilities)" in {
    OpenAIAgent.synchronous(openAI, "llama3-70b").tools(echoTool): Unit
    succeed
  }

  it should "require the shared capabilities of all models a hook can return" in {
    // GPT4o and GPT41 both have ToolCalling — the inferred M supports tools:
    OpenAIAgent
      .synchronous(openAI, (info: IterationInfo) => if (info.isLastIteration) ChatCompletionModel.GPT41 else ChatCompletionModel.GPT4o)
      .tools(echoTool): Unit
    succeed
  }
}
