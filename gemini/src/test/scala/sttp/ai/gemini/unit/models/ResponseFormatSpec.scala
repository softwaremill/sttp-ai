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

  "ResponseFormat.JsonSchema" should "encode with a nested json_schema object" in {
    val schema = parse("""{"type":"object","properties":{"name":{"type":"string"}}}""").value
    val format: ResponseFormat = ResponseFormat.JsonSchema(name = "person", schema = schema)

    format.asJson shouldBe parse(
      """{"type":"json_schema","json_schema":{"name":"person","schema":{"type":"object","properties":{"name":{"type":"string"}}}}}"""
    ).value
  }

  it should "round-trip with description and strict" in {
    val schema = parse("""{"type":"object"}""").value
    val format: ResponseFormat = ResponseFormat.JsonSchema("p", schema, description = Some("d"), strict = Some(true))
    decode[ResponseFormat](format.asJson.noSpaces).value shouldBe format
  }
}
