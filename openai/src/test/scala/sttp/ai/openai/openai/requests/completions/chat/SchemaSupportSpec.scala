package sttp.ai.openai.requests.completions.chat

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.apispec.Schema

class SchemaSupportSpec extends AnyFlatSpec with Matchers with EitherValues {

  private def normalize(rawSchema: String): Json = {
    val schema = parse(rawSchema).value.as[Schema](sttp.apispec.circe.schemaDecoder).value
    SchemaSupport.schemaCodec(schema)
  }

  "strict-mode normalization" should "make an optional property with a simple type nullable" in {
    val result = normalize("""{"type":"object","properties":{"a":{"type":"string"},"b":{"type":"integer"}},"required":["a"]}""")
    val expected = parse(
      """{"type":"object",
        |"properties":{"a":{"type":"string"},"b":{"type":["integer","null"]}},
        |"required":["a","b"],
        |"additionalProperties":false}""".stripMargin
    ).value
    result shouldBe expected
  }

  it should "append null to an optional property that already has a type array" in {
    val result = normalize("""{"type":"object","properties":{"a":{"type":["string","integer"]}},"required":[]}""")
    val expected = parse(
      """{"type":"object",
        |"properties":{"a":{"type":["string","integer","null"]}},
        |"required":["a"],
        |"additionalProperties":false}""".stripMargin
    ).value
    result shouldBe expected
  }

  it should "not add null twice to an already-nullable optional property" in {
    val result = normalize("""{"type":"object","properties":{"a":{"type":["string","null"]}}}""")
    val expected = parse(
      """{"type":"object",
        |"properties":{"a":{"type":["string","null"]}},
        |"required":["a"],
        |"additionalProperties":false}""".stripMargin
    ).value
    result shouldBe expected
  }

  it should "wrap an optional property without a type in anyOf with a null schema" in {
    val result = normalize(
      """{"type":"object","properties":{"a":{"anyOf":[{"type":"string"},{"type":"integer"}]}},"required":[]}"""
    )
    val expected = parse(
      """{"type":"object",
        |"properties":{"a":{"anyOf":[{"anyOf":[{"type":"string"},{"type":"integer"}]},{"type":"null"}]}},
        |"required":["a"],
        |"additionalProperties":false}""".stripMargin
    ).value
    result shouldBe expected
  }

  it should "treat all properties as optional when the object has no required array" in {
    val result = normalize("""{"type":"object","properties":{"a":{"type":"string"}}}""")
    val expected = parse(
      """{"type":"object",
        |"properties":{"a":{"type":["string","null"]}},
        |"required":["a"],
        |"additionalProperties":false}""".stripMargin
    ).value
    result shouldBe expected
  }

  it should "apply nullability recursively inside nested objects" in {
    val result = normalize(
      """{"type":"object",
        |"properties":{
        |  "location":{"type":"object","properties":{"lat":{"type":"number"},"note":{"type":"string"}},"required":["lat"]}
        |},
        |"required":["location"]}""".stripMargin
    )
    val expected = parse(
      """{"type":"object",
        |"properties":{
        |  "location":{"type":"object",
        |    "properties":{"lat":{"type":"number"},"note":{"type":["string","null"]}},
        |    "required":["lat","note"],
        |    "additionalProperties":false}
        |},
        |"required":["location"],
        |"additionalProperties":false}""".stripMargin
    ).value
    result shouldBe expected
  }

  it should "keep originally-required properties unchanged" in {
    val result = normalize("""{"type":"object","properties":{"a":{"type":"string"}},"required":["a"]}""")
    val expected = parse(
      """{"type":"object",
        |"properties":{"a":{"type":"string"}},
        |"required":["a"],
        |"additionalProperties":false}""".stripMargin
    ).value
    result shouldBe expected
  }

  it should "still skip additionalProperties on discriminated unions" in {
    val result = normalize(
      """{"type":"object",
        |"properties":{"kind":{"type":"string"}},
        |"required":["kind"],
        |"discriminator":{"propertyName":"kind"}}""".stripMargin
    )
    result.hcursor.downField("additionalProperties").focus shouldBe None
    result.hcursor.downField("required").as[List[String]] shouldBe Right(List("kind"))
  }
}
