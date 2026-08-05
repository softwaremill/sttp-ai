package sttp.ai.openai.unit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.model.Capability
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel._

class ChatCompletionModelCapabilitySpec extends AnyFlatSpec with Matchers {

  "ChatCompletionModel capability tags" should "mark 4o/4.1/4.5 family as Vision + ToolCalling + StructuredOutput" in
    Seq[ChatCompletionModel](GPT4o, GPT4oMini, GPT41, GPT41Mini, GPT41Nano, GPT45Preview).foreach { m =>
      m shouldBe a[Capability.Vision]
      m shouldBe a[Capability.ToolCalling]
      m shouldBe a[Capability.StructuredOutput]
      m should not be a[Capability.Reasoning]
    }

  it should "mark gpt-5 family as reasoning models (except gpt-5-chat-latest)" in {
    Seq[ChatCompletionModel](GPT5, GPT5Mini, GPT5Nano).foreach { m =>
      m shouldBe a[Capability.Reasoning]
      m shouldBe a[Capability.ToolCalling]
    }
    GPT5ChatLatest should not be a[Capability.Reasoning]
    GPT5ChatLatest should not be a[Capability.ToolCalling]
    GPT5ChatLatest shouldBe a[Capability.Vision]
  }

  it should "mark o-series correctly" in {
    O1Mini shouldBe a[Capability.Reasoning]
    O1Mini should not be a[Capability.ToolCalling]
    O3Mini shouldBe a[Capability.ToolCalling]
    O3Mini should not be a[Capability.Vision]
    O3 shouldBe a[Capability.Vision]
    O4Mini shouldBe a[Capability.Vision]
  }

  it should "leave plain-ToolCalling defaults on legacy gpt-4 / gpt-3.5 models" in {
    Seq[ChatCompletionModel](GPT4, GPT40613, GPT35Turbo).foreach { m =>
      m shouldBe a[Capability.ToolCalling]
      m should not be a[Capability.Vision]
    }
    GPT35TurboInstruct should not be a[Capability.ToolCalling]
    GPT41106VisionPreview shouldBe a[Capability.Vision]
    GPT41106VisionPreview should not be a[Capability.ToolCalling]
  }

  it should "make CustomChatCompletionModel claim all capabilities" in {
    val custom = CustomChatCompletionModel("llama3")
    custom shouldBe a[Capability.Vision]
    custom shouldBe a[Capability.ToolCalling]
    custom shouldBe a[Capability.StructuredOutput]
    custom shouldBe a[Capability.Reasoning]
  }
}
