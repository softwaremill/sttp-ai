package sttp.ai.openai.unit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.model.Capability
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel._

class ChatCompletionModelCapabilitySpec extends AnyFlatSpec with Matchers {

  "ChatCompletionModel capability tags" should "mark 4o/4.1 family as Vision + ToolCalling + StructuredOutput" in
    Seq[ChatCompletionModel](GPT4o, GPT4oMini, GPT4o20240806, GPT41, GPT41Mini).foreach { m =>
      m shouldBe a[Capability.Vision]
      m shouldBe a[Capability.ToolCalling]
      m shouldBe a[Capability.StructuredOutput]
      m should not be a[Capability.Reasoning]
    }

  it should "mark gpt-5.x and gpt-6.x families as reasoning models" in
    Seq[ChatCompletionModel](GPT52, GPT56Sol, GPT56Terra, GPT56Luna, GPT6Sol, GPT6Luna, GPT61Sol, GPT61Terra, GPT62Astra, GPT62Sol)
      .foreach { m =>
        m shouldBe a[Capability.Reasoning]
        m shouldBe a[Capability.ToolCalling]
      }

  it should "make CustomChatCompletionModel claim all capabilities" in {
    val custom = CustomChatCompletionModel("llama3")
    custom shouldBe a[Capability.Vision]
    custom shouldBe a[Capability.ToolCalling]
    custom shouldBe a[Capability.StructuredOutput]
    custom shouldBe a[Capability.Reasoning]
  }
}
