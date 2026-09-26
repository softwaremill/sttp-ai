package sttp.ai.core.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// Top-level so `assertDoesNotCompile` snippets can reference the models by stable, fully-qualified path.
object CapabilitySpecModels {
  sealed abstract class TestModel(val value: String) extends AIModel
  case object ToolModel extends TestModel("tool-model") with Capability.ToolCalling
  case object PlainModel extends TestModel("plain-model")
}

class CapabilitySpec extends AnyFlatSpec with Matchers {
  import CapabilitySpecModels._

  "Supports" should "resolve for a model that mixes in the capability" in {
    implicitly[Supports[ToolModel.type, Capability.ToolCalling]]: Unit
    succeed
  }

  it should "not resolve for a model without the capability" in
    // The positive twin above proves the snippet shape is otherwise valid, so this failure is the missing evidence.
    assertDoesNotCompile(
      "implicitly[sttp.ai.core.model.Supports[sttp.ai.core.model.CapabilitySpecModels.PlainModel.type, sttp.ai.core.model.Capability.ToolCalling]]"
    )

  it should "allow an explicit Supports.assume opt-out for a missing or wrong tag" in {
    implicit val assumed: Supports[PlainModel.type, Capability.ToolCalling] = Supports.assume
    implicitly[Supports[PlainModel.type, Capability.ToolCalling]] shouldBe assumed
    // other capabilities of the same model stay unchecked-in:
    assertDoesNotCompile(
      "implicitly[sttp.ai.core.model.Supports[sttp.ai.core.model.CapabilitySpecModels.PlainModel.type, sttp.ai.core.model.Capability.Vision]]"
    )
  }

  it should "resolve every capability for a model mixing in Capability.All" in {
    object AllShorthandModel extends AIModel with Capability.All {
      val value: String = "all-shorthand"
    }
    implicitly[Supports[AllShorthandModel.type, Capability.Vision]]: Unit
    implicitly[Supports[AllShorthandModel.type, Capability.ToolCalling]]: Unit
    implicitly[Supports[AllShorthandModel.type, Capability.StructuredOutput]]: Unit
    implicitly[Supports[AllShorthandModel.type, Capability.Reasoning]]: Unit
    succeed
  }

  it should "resolve for every capability a model mixes in, independently" in {
    object AllModel
        extends AIModel
        with Capability.Vision
        with Capability.ToolCalling
        with Capability.StructuredOutput
        with Capability.Reasoning {
      val value: String = "all"
    }
    implicitly[Supports[AllModel.type, Capability.Vision]]: Unit
    implicitly[Supports[AllModel.type, Capability.ToolCalling]]: Unit
    implicitly[Supports[AllModel.type, Capability.StructuredOutput]]: Unit
    implicitly[Supports[AllModel.type, Capability.Reasoning]]: Unit
    succeed
  }
}
