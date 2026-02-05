package sttp.ai.claude.unit.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models.ContentBlock
import sttp.ai.core.json.SnakePickle._

class ContentBlockSpec extends AnyFlatSpec with Matchers {

  "TextContent" should "have correct type" in {
    val textContent = ContentBlock.TextContent("Hello")
    textContent.`type` shouldBe "text"
    textContent.text shouldBe "Hello"
  }

  it should "serialize and deserialize correctly" in {
    val textContent = ContentBlock.TextContent("Hello, Claude!")
    val json = write(textContent)
    val deserialized = read[ContentBlock](json)

    deserialized shouldBe textContent
  }

  "ThinkingContent" should "have correct type" in {
    val thinkingContent = ContentBlock.ThinkingContent("Let me think about this...")
    thinkingContent.`type` shouldBe "thinking"
    thinkingContent.thinking shouldBe "Let me think about this..."
  }

  it should "serialize and deserialize correctly" in {
    val thinkingContent = ContentBlock.ThinkingContent("This is a thinking process")
    val json = write(thinkingContent)
    val deserialized = read[ContentBlock](json)

    deserialized shouldBe thinkingContent
  }

  "ImageContent" should "have correct type" in {
    val imageSource = ContentBlock.ImageSource.base64("image/png", "base64data")
    val imageContent = ContentBlock.ImageContent(imageSource)

    imageContent.`type` shouldBe "image"
    imageContent.source.`type` shouldBe "base64"
    imageContent.source.mediaType shouldBe "image/png"
    imageContent.source.data shouldBe "base64data"
  }

  it should "serialize and deserialize correctly" in {
    val imageContent = ContentBlock.ImageContent(
      ContentBlock.ImageSource.base64("image/jpeg", "testdata")
    )
    val json = write(imageContent)
    val deserialized = read[ContentBlock](json)

    deserialized shouldBe imageContent
  }

  "ImageSource" should "create base64 source correctly" in {
    val source = ContentBlock.ImageSource.base64("image/png", "testdata")

    source.`type` shouldBe "base64"
    source.mediaType shouldBe "image/png"
    source.data shouldBe "testdata"
  }
}
