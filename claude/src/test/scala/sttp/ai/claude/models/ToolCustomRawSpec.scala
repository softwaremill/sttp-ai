package sttp.ai.claude.models

import io.circe.parser.parse
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.json.ClaudeDerivedCodecs._
import sttp.ai.claude.json.ClaudeManualCodecs._

class ToolCustomRawSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val nestedSchema = parse(
    """{"type":"object",
      |"properties":{
      |  "title":{"type":"string"},
      |  "location":{"type":"object","properties":{"lat":{"type":"number"},"lng":{"type":"number"}},"required":["lat","lng"]}
      |},
      |"required":["title","location"]}""".stripMargin
  ).value

  "Tool.CustomRaw" should "encode with the raw input schema passed through verbatim" in {
    val tool: Tool = Tool.customRaw("create-event", "Creates an event", nestedSchema)
    val expected = parse(
      s"""{"name":"create-event","description":"Creates an event","input_schema":${nestedSchema.noSpaces}}"""
    ).value
    (tool.asJson: io.circe.Json) shouldBe expected
  }

  it should "include cache_control when set" in {
    val tool: Tool = Tool.CustomRaw("t", "d", nestedSchema, cacheControl = Some(CacheControl.Ephemeral()))
    val expected = parse(
      s"""{"name":"t","description":"d","input_schema":${nestedSchema.noSpaces},"cache_control":{"ttl":null,"type":"ephemeral"}}"""
    ).value
    (tool.asJson: io.circe.Json) shouldBe expected
  }

  it should "encode Custom and CustomRaw with equivalent content identically (encoder dedup regression guard)" in {
    val inputSchema = ToolInputSchema.forObject(Map("q" -> PropertySchema.string("query")))
    val custom: Tool = Tool.Custom("t", "d", inputSchema, cacheControl = Some(CacheControl.Ephemeral()))
    val customRaw: Tool = Tool.CustomRaw("t", "d", inputSchema.asJson, cacheControl = Some(CacheControl.Ephemeral()))
    (custom.asJson: io.circe.Json) shouldBe (customRaw.asJson: io.circe.Json)
  }

  "Tool decoder" should "decode a flat schema as Tool.Custom (historical behavior preserved)" in {
    val json = parse(
      """{"name":"t","description":"d","input_schema":{"type":"object","properties":{"q":{"type":"string"}}}}"""
    ).value
    json.as[Tool].value shouldBe a[Tool.Custom]
  }

  it should "fall back to Tool.CustomRaw for an object schema without properties (Custom cannot represent it)" in {
    val json = parse(
      """{"name":"t","description":"d","input_schema":{"type":"object"}}"""
    ).value
    val schema = parse("""{"type":"object"}""").value
    json.as[Tool].value shouldBe Tool.CustomRaw("t", "d", schema)
  }

  it should "fall back to Tool.CustomRaw for a union type property (Custom cannot represent it)" in {
    val json = parse(
      """{"name":"t","description":"d","input_schema":{"type":"object","properties":{"a":{"type":["string","null"]}}}}"""
    ).value
    val schema = parse("""{"type":"object","properties":{"a":{"type":["string","null"]}}}""").value
    json.as[Tool].value shouldBe Tool.CustomRaw("t", "d", schema)
  }
}
