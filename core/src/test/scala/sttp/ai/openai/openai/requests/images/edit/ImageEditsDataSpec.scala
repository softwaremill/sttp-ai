package sttp.openai.requests.images.edit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.{BasicBodyPart, BodyPart, FileBody, MultipartBody, StringBody}
import sttp.model.{Part, Uri}
import sttp.openai.OpenAI
import sttp.openai.fixtures.ImageEditsFixture

class ImageEditsDataSpec extends AnyFlatSpec with Matchers with ImageEditsFixture {
  val openAI = new OpenAI("test-key", Uri.unsafeParse("https://api.openai.com/v1"))

  private def checkFilePart(parts: Seq[Part[BodyPart[BasicBodyPart]]], name: String, file: java.io.File) = {
    val part = parts.find(_.name == name).get
    part.name shouldBe name
    part.body shouldBe a[FileBody]
    part.headers should contain(sttp.model.Header("Content-Type", "application/octet-stream"))
    part.otherDispositionParams should contain("filename" -> file.getName)
  }

  private def checkStringPart(parts: Seq[Part[BodyPart[BasicBodyPart]]], name: String, value: String) = {
    val part = parts.find(_.name == name).get
    part.name shouldBe name
    part.body shouldBe a[StringBody]
    part.body.asInstanceOf[StringBody].s shouldBe value
    part.headers should contain(sttp.model.Header("Content-Type", "text/plain; charset=utf-8"))
  }

  "imageEdits method" should "create correct request with minimal config (single image)" in {
    // given
    val config = minimalImageEditsConfig

    // when
    val request = openAI.imageEdits(config)
    val body = request.body.asInstanceOf[MultipartBody[BasicBodyPart]]
    val parts = body.parts

    // then
    parts should have size 2

    checkFilePart(parts, "image", config.image.head)
    parts.find(_.name == "image[]") shouldBe None
    checkStringPart(parts, "prompt", config.prompt)
  }

  "imageEdits method" should "create correct request with all parameters for single image" in {
    // given
    val config = imageEditsConfigWithAllParametersSet

    // when
    val request = openAI.imageEdits(config)
    val body = request.body.asInstanceOf[MultipartBody[BasicBodyPart]]
    val parts = body.parts

    // then
    parts should have size 15

    checkFilePart(parts, "image", config.image.head)
    parts.find(_.name == "image[]") shouldBe None
    checkFilePart(parts, "mask", config.mask.get)

    checkStringPart(parts, "prompt", config.prompt)
    checkStringPart(parts, "background", "transparent")
    checkStringPart(parts, "input_fidelity", "high")
    checkStringPart(parts, "model", "gpt-image-1")
    checkStringPart(parts, "n", "2")
    checkStringPart(parts, "output_compression", "80")
    checkStringPart(parts, "output_format", "png")
    checkStringPart(parts, "partial_images", "2")
    checkStringPart(parts, "quality", "high")
    checkStringPart(parts, "size", "1024x1024")
    checkStringPart(parts, "response_format", "url")
    checkStringPart(parts, "stream", "false")
    checkStringPart(parts, "user", "test-user")
  }

  "imageEdits method" should "create correct request with multiple images" in {
    // given
    val config = multiImageEditsConfig

    // when
    val request = openAI.imageEdits(config)
    val body = request.body.asInstanceOf[MultipartBody[BasicBodyPart]]
    val parts = body.parts

    // then
    parts should have size 16

    parts.find(_.name == "image") shouldBe None

    val imageParts = parts.filter(_.name == "image[]")
    imageParts should have size 2
    imageParts.zipWithIndex.foreach { case (part, idx) =>
      val singlePartSeq = Seq(part)
      checkFilePart(singlePartSeq, "image[]", config.image(idx))
    }

    checkStringPart(parts, "prompt", config.prompt)
  }

  "imageEdits method" should "handle empty optional parameters" in {
    // given
    val config = minimalImageEditsConfig

    // when
    val request = openAI.imageEdits(config)
    val body = request.body.asInstanceOf[MultipartBody[BasicBodyPart]]
    val parts = body.parts

    // then
    parts.map(_.name) should not contain "background"
    parts.map(_.name) should not contain "input_fidelity"
    parts.map(_.name) should not contain "mask"
    parts.map(_.name) should not contain "model"
    parts.map(_.name) should not contain "n"
    parts.map(_.name) should not contain "output_compression"
    parts.map(_.name) should not contain "output_format"
    parts.map(_.name) should not contain "partial_images"
    parts.map(_.name) should not contain "quality"
    parts.map(_.name) should not contain "size"
    parts.map(_.name) should not contain "response_format"
    parts.map(_.name) should not contain "stream"
    parts.map(_.name) should not contain "user"
  }
}
