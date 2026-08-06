package sttp.ai.gemini.unit.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.model.Capability
import sttp.ai.gemini.models.GeminiModel

class GeminiModelSpec extends AnyFlatSpec with Matchers {

  "GeminiModel.fromString" should "resolve each known model value to itself" in
    GeminiModel.values.foreach { model =>
      GeminiModel.fromString(model.value) shouldBe model
    }

  it should "resolve an unrecognized model id to CustomModel" in {
    GeminiModel.fromString("some-new-model") shouldBe GeminiModel.CustomModel("some-new-model")
  }

  "GeminiModel.values" should "contain no duplicates" in {
    GeminiModel.values.distinct shouldBe GeminiModel.values
  }

  it should "have a non-empty value for every model" in
    GeminiModel.values.foreach(_.value should not be empty)

  "GeminiModel" should "tag every predefined model with all four capabilities" in
    GeminiModel.values.foreach { m =>
      m shouldBe a[Capability.Vision]
      m shouldBe a[Capability.ToolCalling]
      m shouldBe a[Capability.StructuredOutput]
      m shouldBe a[Capability.Reasoning]
    }

  it should "make CustomModel claim all capabilities" in {
    val custom = GeminiModel.CustomModel("my-gemini")
    custom shouldBe a[Capability.Vision]
    custom shouldBe a[Capability.ToolCalling]
    custom shouldBe a[Capability.StructuredOutput]
    custom shouldBe a[Capability.Reasoning]
  }
}
