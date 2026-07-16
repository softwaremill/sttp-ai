package sttp.ai.claude.models

import io.circe.parser.parse
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
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
    val tool: Tool = Tool.custom("create-event", "Creates an event", nestedSchema)
    val expected = parse(
      s"""{"name":"create-event","description":"Creates an event","input_schema":${nestedSchema.noSpaces}}"""
    ).value
    (tool.asJson: io.circe.Json) shouldBe expected
  }

  it should "include cache_control when set" in {
    val tool: Tool = Tool.CustomRaw("t", "d", nestedSchema, cacheControl = Some(CacheControl.Ephemeral()))
    tool.asJson.hcursor.downField("cache_control").succeeded shouldBe true
  }
}
