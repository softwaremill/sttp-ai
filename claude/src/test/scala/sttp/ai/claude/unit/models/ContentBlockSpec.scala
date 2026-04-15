package sttp.ai.claude.unit.models

import org.scalatest.Inside.inside
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
    inside(imageContent.source) { case ContentBlock.ImageSource.Base64ImageSource(mediaType, data) =>
      mediaType shouldBe "image/png"
      data shouldBe "base64data"
    }
  }

  it should "have correct type for URL source" in {
    val imageSource = ContentBlock.ImageSource.url("http://example.com/image.png")
    val imageContent = ContentBlock.ImageContent(imageSource)

    imageContent.`type` shouldBe "image"
    imageContent.source.`type` shouldBe "url"
    inside(imageContent.source) { case ContentBlock.ImageSource.URLImageSource(url) =>
      url shouldBe "http://example.com/image.png"
    }
  }

  it should "serialize and deserialize correctly" in {
    val imageContent = ContentBlock.ImageContent(
      ContentBlock.ImageSource.base64("image/jpeg", "testdata")
    )
    val json = write(imageContent)
    val deserialized = read[ContentBlock](json)

    deserialized shouldBe imageContent
  }

  it should "serialize and deserialize URL source correctly" in {
    val imageContent = ContentBlock.ImageContent(
      ContentBlock.ImageSource.url("http://example.com/image.png")
    )
    val json = write(imageContent)
    val deserialized = read[ContentBlock](json)

    deserialized shouldBe imageContent
  }

  "ImageSource" should "create base64 source correctly" in {
    val source = ContentBlock.ImageSource.base64("image/png", "testdata")

    source.`type` shouldBe "base64"
    inside(source) { case ContentBlock.ImageSource.Base64ImageSource(mediaType, data) =>
      mediaType shouldBe "image/png"
      data shouldBe "testdata"
    }
  }

  it should "create URL source correctly" in {
    val source = ContentBlock.ImageSource.url("http://example.com/image.png")

    source.`type` shouldBe "url"
    inside(source) { case ContentBlock.ImageSource.URLImageSource(url) =>
      url shouldBe "http://example.com/image.png"
    }
  }
}
