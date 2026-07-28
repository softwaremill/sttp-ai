package sttp.ai.gemini.unit.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
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
}
