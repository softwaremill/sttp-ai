package sttp.ai.claude.unit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.ClaudeClientImpl
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.Message
import sttp.ai.claude.requests.MessageRequest

import scala.concurrent.duration.{Duration, DurationInt}

class ClaudeClientTimeoutSpec extends AnyFlatSpec with Matchers {

  private def request = MessageRequest.simple(
    model = "claude-haiku-4-5-20251001",
    messages = List(Message.user("hi")),
    maxTokens = 10
  )

  private def client(timeout: Duration) = new ClaudeClientImpl(ClaudeConfig(apiKey = "test-key", timeout = timeout))

  "ClaudeClientImpl" should "apply the configured timeout to createMessage requests" in {
    client(5.seconds).createMessage(request).options.readTimeout shouldBe 5.seconds
  }

  it should "apply the configured timeout to streaming (input stream) requests" in {
    client(5.seconds).createMessageAsInputStream(request).options.readTimeout shouldBe 5.seconds
  }

  it should "apply the configured timeout to listModels requests" in {
    client(5.seconds).listModels().options.readTimeout shouldBe 5.seconds
  }

  it should "apply the default 10-minute timeout when none is configured" in {
    new ClaudeClientImpl(ClaudeConfig(apiKey = "test-key")).createMessage(request).options.readTimeout shouldBe ClaudeConfig.DefaultTimeout
  }

  it should "carry a non-finite timeout through (disabling the backend timeout)" in {
    client(Duration.Inf).createMessage(request).options.readTimeout shouldBe Duration.Inf
  }
}
