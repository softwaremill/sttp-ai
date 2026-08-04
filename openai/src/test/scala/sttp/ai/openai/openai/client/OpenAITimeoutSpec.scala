package sttp.ai.openai.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.OpenAI
import sttp.ai.openai.config.OpenAIConfig
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}

import scala.concurrent.duration.{Duration, DurationInt}

class OpenAITimeoutSpec extends AnyFlatSpec with Matchers {

  private val chatBody = ChatBody(Nil, ChatCompletionModel.GPT4oMini)

  private def client(timeout: Duration): OpenAI = OpenAI(OpenAIConfig(apiKey = "test-key", timeout = timeout))

  "OpenAI" should "apply the configured timeout to chat completion requests" in {
    client(5.seconds).createChatCompletion(chatBody).options.readTimeout shouldBe 5.seconds
  }

  it should "apply the configured timeout to getModels requests" in {
    client(5.seconds).getModels.options.readTimeout shouldBe 5.seconds
  }

  it should "apply the configured timeout to streaming (input stream) requests" in {
    client(5.seconds).createChatCompletionAsInputStream(chatBody).options.readTimeout shouldBe 5.seconds
  }

  it should "apply the default 10-minute timeout when constructed without one" in {
    new OpenAI("test-key").getModels.options.readTimeout shouldBe OpenAIConfig.DefaultTimeout
  }

  it should "carry a non-finite timeout through (disabling the backend timeout)" in {
    client(Duration.Inf).getModels.options.readTimeout shouldBe Duration.Inf
  }
}
