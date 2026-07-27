package sttp.ai.gemini.unit.models

import io.circe.parser.{decode, parse}
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.json.GeminiDerivedCodecs._
import sttp.ai.gemini.models.Content

class ContentSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Content.Text" should "encode with a type discriminator and snake_case fields" in {
    val json = (Content.Text("hello"): Content).asJson.deepDropNullValues
    json shouldBe parse("""{"type":"text","text":"hello"}""").value
  }

  it should "decode from discriminated JSON" in {
    decode[Content]("""{"type":"text","text":"hi"}""").value shouldBe Content.Text("hi")
  }

  "Content.Image" should "round-trip with mime_type in snake_case" in {
    val image: Content = Content.Image(data = Some("base64data"), mimeType = Some("image/png"))
    val json = image.asJson.deepDropNullValues
    json shouldBe parse("""{"type":"image","data":"base64data","mime_type":"image/png"}""").value
    decode[Content](json.noSpaces).value shouldBe image
  }
}
