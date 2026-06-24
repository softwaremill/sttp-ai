package sttp.ai.claude.unit.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models._
import io.circe.parser.{decode, parse}
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.OptionValues
import sttp.ai.claude.json.ClaudeDerivedCodecs._
import sttp.ai.claude.json.ClaudeManualCodecs._

class ToolSpec extends AnyFlatSpec with Matchers with EitherValues with OptionValues {

  "Tool.Custom" should "serialize without a type discriminator" in {
    val tool = Tool.Custom(
      name = "get_weather",
      description = "Get weather for a city",
      inputSchema = ToolInputSchema.forObject(
        properties = Map("city" -> PropertySchema.string("The city name")),
        required = Some(List("city"))
      )
    )

    val json = parse((tool: Tool).asJson.deepDropNullValues.noSpaces).value

    json.asObject.value.contains("type") shouldBe false
    json.hcursor.get[String]("name").value shouldBe "get_weather"
    json.hcursor.get[String]("description").value shouldBe "Get weather for a city"
    json.hcursor.downField("input_schema").get[String]("type").value shouldBe "object"
  }

  it should "round-trip" in {
    val tool: Tool = Tool.Custom(
      name = "get_weather",
      description = "desc",
      inputSchema = ToolInputSchema.forObject(
        properties = Map("city" -> PropertySchema.string("city")),
        required = Some(List("city"))
      )
    )
    decode[Tool](tool.asJson.deepDropNullValues.noSpaces).value shouldBe tool
  }

  "Tool.WebSearch" should "serialize with type and name discriminators" in {
    val tool = Tool.WebSearch(
      maxUses = Some(5),
      allowedDomains = Some(List("example.com")),
      userLocation = Some(UserLocation.approximate(city = Some("San Francisco"), country = Some("US")))
    )

    val json = parse((tool: Tool).asJson.deepDropNullValues.noSpaces).value

    json.hcursor.get[String]("type").value shouldBe "web_search_20250305"
    json.hcursor.get[String]("name").value shouldBe "web_search"
    json.hcursor.get[Int]("max_uses").value shouldBe 5
    json.hcursor.get[List[String]]("allowed_domains").value shouldBe List("example.com")
    json.hcursor.downField("user_location").get[String]("type").value shouldBe "approximate"
    json.hcursor.downField("user_location").get[String]("city").value shouldBe "San Francisco"
    json.hcursor.downField("user_location").get[String]("country").value shouldBe "US"
  }

  it should "omit unset fields" in {
    val tool: Tool = Tool.WebSearch()
    val json = parse((tool: Tool).asJson.deepDropNullValues.noSpaces).value

    json.hcursor.get[String]("type").value shouldBe "web_search_20250305"
    json.hcursor.get[String]("name").value shouldBe "web_search"
    json.asObject.value.contains("max_uses") shouldBe false
    json.asObject.value.contains("allowed_domains") shouldBe false
    json.asObject.value.contains("blocked_domains") shouldBe false
    json.asObject.value.contains("user_location") shouldBe false
  }

  it should "round-trip" in {
    val tool: Tool = Tool.WebSearch(
      maxUses = Some(3),
      blockedDomains = Some(List("bad.example")),
      userLocation = Some(UserLocation.approximate(timezone = Some("America/Los_Angeles")))
    )
    decode[Tool](tool.asJson.deepDropNullValues.noSpaces).value shouldBe tool
  }

  "Tool list" should "mix custom and predefined tools in a single array" in {
    val tools: List[Tool] = List(
      Tool.Custom(
        name = "get_weather",
        description = "weather",
        inputSchema = ToolInputSchema.forObject(Map("city" -> PropertySchema.string("city")))
      ),
      Tool.WebSearch(maxUses = Some(5))
    )

    val arr = parse(tools.asJson.deepDropNullValues.noSpaces).value.asArray.value.toList

    arr.head.asObject.value.contains("type") shouldBe false
    arr(1).hcursor.get[String]("type").value shouldBe "web_search_20250305"

    decode[List[Tool]](tools.asJson.deepDropNullValues.noSpaces).value shouldBe tools
  }
}
