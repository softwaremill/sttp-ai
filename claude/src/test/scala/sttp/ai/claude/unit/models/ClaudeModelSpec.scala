package sttp.ai.claude.unit.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models.ClaudeModel
import sttp.ai.core.model.Capability

class ClaudeModelSpec extends AnyFlatSpec with Matchers {

  "ClaudeModel.fromString" should "resolve each known model value to itself" in
    ClaudeModel.values.foreach(m => ClaudeModel.fromString(m.value) shouldBe Some(m))

  it should "return None for an unknown model" in {
    ClaudeModel.fromString("claude-3-haiku-20240307") shouldBe None
  }

  "ClaudeModel" should "tag every predefined model with all four capabilities" in
    ClaudeModel.values.foreach { m =>
      m shouldBe a[Capability.Vision]
      m shouldBe a[Capability.ToolCalling]
      m shouldBe a[Capability.StructuredOutput]
      m shouldBe a[Capability.Reasoning]
    }

  it should "make CustomClaudeModel claim all capabilities" in {
    val custom = ClaudeModel.CustomClaudeModel("my-model")
    custom shouldBe a[Capability.Vision]
    custom shouldBe a[Capability.ToolCalling]
    custom shouldBe a[Capability.StructuredOutput]
    custom shouldBe a[Capability.Reasoning]
    ClaudeModel.values should not contain custom
  }
}
