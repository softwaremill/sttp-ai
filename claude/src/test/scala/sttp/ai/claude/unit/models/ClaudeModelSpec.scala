package sttp.ai.claude.unit.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models.ClaudeModel
import sttp.ai.core.model.Capability

class ClaudeModelSpec extends AnyFlatSpec with Matchers {

  "ClaudeModel" should "have correct string values" in {
    ClaudeModel.Claude3_5Sonnet.value shouldBe "claude-3-5-sonnet-20241022"
    ClaudeModel.Claude3_5SonnetLatest.value shouldBe "claude-3-5-sonnet-latest"
    ClaudeModel.Claude3_5Haiku.value shouldBe "claude-3-5-haiku-20241022"
    ClaudeModel.Claude3_5HaikuLatest.value shouldBe "claude-3-5-haiku-latest"
    ClaudeModel.Claude3Opus.value shouldBe "claude-3-opus-20240229"
    ClaudeModel.Claude3Sonnet.value shouldBe "claude-3-sonnet-20240229"
    ClaudeModel.Claude3Haiku.value shouldBe "claude-3-haiku-20240307"
    ClaudeModel.ClaudeSonnet4_0.value shouldBe "claude-sonnet-4-20250514"
    ClaudeModel.ClaudeOpus4_0.value shouldBe "claude-opus-4-20250514"
    ClaudeModel.ClaudeOpus4_1.value shouldBe "claude-opus-4-1-20250805"
    ClaudeModel.ClaudeSonnet4_5.value shouldBe "claude-sonnet-4-5-20250929"
    ClaudeModel.ClaudeSonnet4_5Latest.value shouldBe "claude-sonnet-4-5"
    ClaudeModel.ClaudeHaiku4_5.value shouldBe "claude-haiku-4-5-20251001"
    ClaudeModel.ClaudeHaiku4_5Latest.value shouldBe "claude-haiku-4-5"
    ClaudeModel.ClaudeOpus4_5.value shouldBe "claude-opus-4-5-20251101"
    ClaudeModel.ClaudeOpus4_5Latest.value shouldBe "claude-opus-4-5"
    ClaudeModel.ClaudeSonnet5.value shouldBe "claude-sonnet-5"
    ClaudeModel.ClaudeOpus5.value shouldBe "claude-opus-5"
  }

  it should "convert toString correctly" in {
    ClaudeModel.Claude3_5Sonnet.toString shouldBe "claude-3-5-sonnet-20241022"
    ClaudeModel.Claude3Opus.toString shouldBe "claude-3-opus-20240229"
    ClaudeModel.ClaudeSonnet4_0.toString shouldBe "claude-sonnet-4-20250514"
    ClaudeModel.ClaudeOpus4_5.toString shouldBe "claude-opus-4-5-20251101"
  }

  it should "find models from string values" in {
    ClaudeModel.fromString("claude-3-5-sonnet-20241022") shouldBe Some(ClaudeModel.Claude3_5Sonnet)
    ClaudeModel.fromString("claude-3-opus-20240229") shouldBe Some(ClaudeModel.Claude3Opus)
    ClaudeModel.fromString("claude-sonnet-4-20250514") shouldBe Some(ClaudeModel.ClaudeSonnet4_0)
    ClaudeModel.fromString("claude-opus-4-5-20251101") shouldBe Some(ClaudeModel.ClaudeOpus4_5)
    ClaudeModel.fromString("invalid-model") shouldBe None
  }

  it should "have all models in values set" in {
    ClaudeModel.values should contain(ClaudeModel.Claude3_5Sonnet)
    ClaudeModel.values should contain(ClaudeModel.Claude3_5SonnetLatest)
    ClaudeModel.values should contain(ClaudeModel.Claude3_5Haiku)
    ClaudeModel.values should contain(ClaudeModel.Claude3_5HaikuLatest)
    ClaudeModel.values should contain(ClaudeModel.Claude3Opus)
    ClaudeModel.values should contain(ClaudeModel.Claude3Sonnet)
    ClaudeModel.values should contain(ClaudeModel.Claude3Haiku)
    ClaudeModel.values should contain(ClaudeModel.ClaudeSonnet4_0)
    ClaudeModel.values should contain(ClaudeModel.ClaudeOpus4_0)
    ClaudeModel.values should contain(ClaudeModel.ClaudeHaiku4_5)
    ClaudeModel.values should contain(ClaudeModel.ClaudeHaiku4_5Latest)
    ClaudeModel.values should contain(ClaudeModel.ClaudeOpus4_1)
    ClaudeModel.values should contain(ClaudeModel.ClaudeSonnet4_5)
    ClaudeModel.values should contain(ClaudeModel.ClaudeSonnet4_5Latest)
    ClaudeModel.values should contain(ClaudeModel.ClaudeOpus4_5)
    ClaudeModel.values should contain(ClaudeModel.ClaudeOpus4_5Latest)
    ClaudeModel.values should contain(ClaudeModel.ClaudeSonnet5)
    ClaudeModel.values should contain(ClaudeModel.ClaudeOpus5)
    ClaudeModel.values should have size 18
  }

  "Capability tags" should "not include StructuredOutput on legacy 3.x and 4.0 models" in {
    ClaudeModel.Claude3_5Sonnet should not be a[Capability.StructuredOutput]
    ClaudeModel.Claude3_5SonnetLatest should not be a[Capability.StructuredOutput]
    ClaudeModel.Claude3_5Haiku should not be a[Capability.StructuredOutput]
    ClaudeModel.Claude3_5HaikuLatest should not be a[Capability.StructuredOutput]
    ClaudeModel.Claude3Opus should not be a[Capability.StructuredOutput]
    ClaudeModel.Claude3Sonnet should not be a[Capability.StructuredOutput]
    ClaudeModel.Claude3Haiku should not be a[Capability.StructuredOutput]
    ClaudeModel.ClaudeSonnet4_0 should not be a[Capability.StructuredOutput]
    ClaudeModel.ClaudeOpus4_0 should not be a[Capability.StructuredOutput]
  }

  it should "include StructuredOutput on 4.1+, 4.5 and 5 models" in {
    ClaudeModel.ClaudeOpus4_1 shouldBe a[Capability.StructuredOutput]
    ClaudeModel.ClaudeSonnet4_5 shouldBe a[Capability.StructuredOutput]
    ClaudeModel.ClaudeSonnet4_5Latest shouldBe a[Capability.StructuredOutput]
    ClaudeModel.ClaudeHaiku4_5 shouldBe a[Capability.StructuredOutput]
    ClaudeModel.ClaudeHaiku4_5Latest shouldBe a[Capability.StructuredOutput]
    ClaudeModel.ClaudeOpus4_5 shouldBe a[Capability.StructuredOutput]
    ClaudeModel.ClaudeOpus4_5Latest shouldBe a[Capability.StructuredOutput]
    ClaudeModel.ClaudeSonnet5 shouldBe a[Capability.StructuredOutput]
    ClaudeModel.ClaudeOpus5 shouldBe a[Capability.StructuredOutput]
  }

  it should "mark every predefined model as ToolCalling, and 3.5 Haiku as text-only" in {
    ClaudeModel.values.foreach(m => m shouldBe a[Capability.ToolCalling])
    ClaudeModel.Claude3_5Haiku should not be a[Capability.Vision]
    ClaudeModel.Claude3_5HaikuLatest should not be a[Capability.Vision]
    ClaudeModel.Claude3Haiku shouldBe a[Capability.Vision]
    ClaudeModel.ClaudeSonnet5 shouldBe a[Capability.Vision]
  }

  it should "make CustomClaudeModel claim all capabilities" in {
    val custom = ClaudeModel.CustomClaudeModel("my-model")
    custom shouldBe a[Capability.Vision]
    custom shouldBe a[Capability.ToolCalling]
    custom shouldBe a[Capability.StructuredOutput]
    custom shouldBe a[Capability.Reasoning]
    custom.value shouldBe "my-model"
    ClaudeModel.values should not contain custom
  }

  "modelSupportsStructuredOutput" should "return false for known legacy model IDs" in {
    ClaudeModel.modelSupportsStructuredOutput("claude-3-5-sonnet-20241022") shouldBe false
    ClaudeModel.modelSupportsStructuredOutput("claude-3-5-sonnet-latest") shouldBe false
    ClaudeModel.modelSupportsStructuredOutput("claude-3-5-haiku-20241022") shouldBe false
    ClaudeModel.modelSupportsStructuredOutput("claude-3-5-haiku-latest") shouldBe false
    ClaudeModel.modelSupportsStructuredOutput("claude-3-opus-20240229") shouldBe false
    ClaudeModel.modelSupportsStructuredOutput("claude-3-sonnet-20240229") shouldBe false
    ClaudeModel.modelSupportsStructuredOutput("claude-3-haiku-20240307") shouldBe false
  }

  it should "return false for Claude 4.0 model IDs" in {
    ClaudeModel.modelSupportsStructuredOutput("claude-sonnet-4-20250514") shouldBe false
    ClaudeModel.modelSupportsStructuredOutput("claude-opus-4-20250514") shouldBe false
  }

  it should "return true for Claude 4.1 model IDs" in {
    ClaudeModel.modelSupportsStructuredOutput("claude-opus-4-1-20250805") shouldBe true
  }

  it should "return true for Claude 4.5 model IDs" in {
    ClaudeModel.modelSupportsStructuredOutput("claude-sonnet-4-5-20250929") shouldBe true
    ClaudeModel.modelSupportsStructuredOutput("claude-sonnet-4-5") shouldBe true
    ClaudeModel.modelSupportsStructuredOutput("claude-haiku-4-5-20251001") shouldBe true
    ClaudeModel.modelSupportsStructuredOutput("claude-haiku-4-5") shouldBe true
    ClaudeModel.modelSupportsStructuredOutput("claude-opus-4-5-20251101") shouldBe true
    ClaudeModel.modelSupportsStructuredOutput("claude-opus-4-5") shouldBe true
  }

  it should "default to true for unknown model IDs (forward compatibility)" in {
    ClaudeModel.modelSupportsStructuredOutput("claude-future-model-20260101") shouldBe true
    ClaudeModel.modelSupportsStructuredOutput("some-unknown-model") shouldBe true
    ClaudeModel.modelSupportsStructuredOutput("claude-5-sonnet-20260101") shouldBe true
  }
}
