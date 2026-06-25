package sttp.ai.claude.unit.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models.{ContentBlock, Message}
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.EitherValues
import sttp.ai.claude.json.ClaudeDerivedCodecs._
import sttp.ai.claude.json.ClaudeManualCodecs._

class MessageSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Message" should "create user message with text" in {
    val message = Message.user("Hello, Claude!")

    message.role shouldBe "user"
    message.content should have size 1
    message.content.head shouldBe ContentBlock.Text("Hello, Claude!")
  }

  it should "create assistant message with text" in {
    val message = Message.assistant("Hello! How can I help you?")

    message.role shouldBe "assistant"
    message.content should have size 1
    message.content.head shouldBe ContentBlock.Text("Hello! How can I help you?")
  }

  it should "serialize and deserialize correctly" in {
    val message = Message.user("Test message")
    val json = message.asJson.deepDropNullValues.noSpaces
    val deserialized = decode[Message](json).value

    deserialized shouldBe message
  }

  it should "handle mixed content blocks" in {
    val textContent = ContentBlock.Text("Here's an image:")
    val imageContent = ContentBlock.Image(
      ContentBlock.ImageSource.base64("image/png", "base64data")
    )
    val message = Message.user(List(textContent, imageContent))

    message.content should have size 2
    message.content(0) shouldBe textContent
    message.content(1) shouldBe imageContent
  }
}
