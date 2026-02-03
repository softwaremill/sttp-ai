package sttp.ai.claude.unit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.ClaudeExceptions.UnsupportedModelForStructuredOutputException

class StructuredOutputValidationSpec extends AnyFlatSpec with Matchers {

  "UnsupportedModelForStructuredOutputException" should "include model ID in message" in {
    val exception = new UnsupportedModelForStructuredOutputException("claude-3-5-sonnet-20241022")

    exception.getMessage should include("claude-3-5-sonnet-20241022")
    exception.getMessage should include("does not support structured output")
  }

  it should "suggest supported models" in {
    val exception = new UnsupportedModelForStructuredOutputException("claude-3-opus-20240229")

    exception.getMessage should include("Claude 4.x models")
  }

  it should "extend Exception" in {
    val exception = new UnsupportedModelForStructuredOutputException("test-model")

    exception shouldBe a[Exception]
  }
}
