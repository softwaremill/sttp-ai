package sttp.ai.claude.unit.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models.{ContentBlock, Message}
import sttp.ai.core.json.SnakePickle._

class MessageSpec extends AnyFlatSpec with Matchers {

  "Message" should "create user message with text" in {
    val message = Message.user("Hello, Claude!")

    message.role shouldBe "user"
    message.content should have size 1
    message.content.head shouldBe ContentBlock.TextContent("Hello, Claude!")
  }

  it should "create assistant message with text" in {
    val message = Message.assistant("Hello! How can I help you?")

    message.role shouldBe "assistant"
    message.content should have size 1
    message.content.head shouldBe ContentBlock.TextContent("Hello! How can I help you?")
  }

  it should "serialize and deserialize correctly" in {
    val message = Message.user("Test message")
    val json = write(message)
    val deserialized = read[Message](json)

    deserialized shouldBe message
  }

  it should "handle mixed content blocks" in {
    val textContent = ContentBlock.TextContent("Here's an image:")
    val imageContent = ContentBlock.ImageContent(
      ContentBlock.ImageSource.base64("image/png", "base64data")
    )
    val message = Message.user(List(textContent, imageContent))

    message.content should have size 2
    message.content(0) shouldBe textContent
    message.content(1) shouldBe imageContent
  }
}
