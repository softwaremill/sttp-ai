package sttp.ai.gemini.unit.models

import io.circe.parser.{decode, parse}
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.json.GeminiManualCodecs._
import sttp.ai.gemini.models.ResponseFormat

class ResponseFormatSpec extends AnyFlatSpec with Matchers with EitherValues {

  "ResponseFormat.Text" should "encode as bare type object" in {
    (ResponseFormat.Text: ResponseFormat).asJson shouldBe parse("""{"type":"text"}""").value
  }

  it should "round-trip" in {
    val format: ResponseFormat = ResponseFormat.Text
    decode[ResponseFormat](format.asJson.noSpaces).value shouldBe format
  }

  "ResponseFormat.JsonSchema" should "encode the schema verbatim, without a json_schema envelope" in {
    val schema = parse("""{"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}""").value
    val format: ResponseFormat = ResponseFormat.JsonSchema(schema)

    format.asJson shouldBe schema
  }

  it should "decode a schema object (no type=text) as JsonSchema" in {
    val schemaJson = """{"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}"""
    val schema = parse(schemaJson).value

    decode[ResponseFormat](schemaJson).value shouldBe ResponseFormat.JsonSchema(schema)
  }

  it should "round-trip through encode/decode" in {
    val schema = parse("""{"type":"object"}""").value
    val format: ResponseFormat = ResponseFormat.JsonSchema(schema)
    decode[ResponseFormat](format.asJson.noSpaces).value shouldBe format
  }
}
