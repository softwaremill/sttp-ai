package sttp.ai.claude.unit.models

import org.scalatest.Inside.inside
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models.ContentBlock
import io.circe.parser.{decode, parse}
import io.circe.syntax._
import org.scalatest.EitherValues
import sttp.ai.claude.json.ClaudeDerivedCodecs._
import sttp.ai.claude.json.ClaudeManualCodecs._

class ContentBlockSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Text" should "have correct type" in {
    val textContent = ContentBlock.Text("Hello")
    parse((textContent: ContentBlock).asJson.deepDropNullValues.noSpaces).value.hcursor.get[String]("type").value shouldBe "text"
    textContent.text shouldBe "Hello"
  }

  it should "serialize and deserialize correctly" in {
    val textContent: ContentBlock = ContentBlock.Text("Hello, Claude!")
    val json = textContent.asJson.deepDropNullValues.noSpaces
    val deserialized = decode[ContentBlock](json).value

    deserialized shouldBe textContent
  }

  "Thinking" should "have correct type" in {
    val thinkingContent = ContentBlock.Thinking("Let me think about this...")
    parse((thinkingContent: ContentBlock).asJson.deepDropNullValues.noSpaces).value.hcursor.get[String]("type").value shouldBe "thinking"
    thinkingContent.thinking shouldBe "Let me think about this..."
  }

  it should "serialize and deserialize correctly" in {
    val thinkingContent: ContentBlock = ContentBlock.Thinking("This is a thinking process")
    val json = thinkingContent.asJson.deepDropNullValues.noSpaces
    val deserialized = decode[ContentBlock](json).value

    deserialized shouldBe thinkingContent
  }

  "Image" should "have correct type" in {
    val imageSource = ContentBlock.ImageSource.base64("image/png", "base64data")
    val imageContent = ContentBlock.Image(imageSource)

    parse((imageContent: ContentBlock).asJson.deepDropNullValues.noSpaces).value.hcursor.get[String]("type").value shouldBe "image"
    inside(imageContent.source) { case ContentBlock.ImageSource.Base64(mediaType, data) =>
      mediaType shouldBe "image/png"
      data shouldBe "base64data"
    }
  }

  it should "have correct type for URL source" in {
    val imageSource = ContentBlock.ImageSource.url("http://example.com/image.png")
    val imageContent = ContentBlock.Image(imageSource)

    parse((imageContent: ContentBlock).asJson.deepDropNullValues.noSpaces).value.hcursor.get[String]("type").value shouldBe "image"
    inside(imageContent.source) { case ContentBlock.ImageSource.Url(url) =>
      url shouldBe "http://example.com/image.png"
    }
  }

  it should "serialize and deserialize correctly" in {
    val imageContent: ContentBlock = ContentBlock.Image(
      ContentBlock.ImageSource.base64("image/jpeg", "testdata")
    )
    val json = imageContent.asJson.deepDropNullValues.noSpaces
    val deserialized = decode[ContentBlock](json).value

    deserialized shouldBe imageContent
  }

  it should "serialize and deserialize URL source correctly" in {
    val imageContent: ContentBlock = ContentBlock.Image(
      ContentBlock.ImageSource.url("http://example.com/image.png")
    )
    val json = imageContent.asJson.deepDropNullValues.noSpaces
    val deserialized = decode[ContentBlock](json).value

    deserialized shouldBe imageContent
  }

  "ImageSource" should "create base64 source correctly" in {
    val source = ContentBlock.ImageSource.base64("image/png", "testdata")

    inside(source) { case ContentBlock.ImageSource.Base64(mediaType, data) =>
      mediaType shouldBe "image/png"
      data shouldBe "testdata"
    }
  }

  it should "create URL source correctly" in {
    val source = ContentBlock.ImageSource.url("http://example.com/image.png")

    inside(source) { case ContentBlock.ImageSource.Url(url) =>
      url shouldBe "http://example.com/image.png"
    }
  }
}
