package sttp.ai.claude.unit.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models._
import sttp.ai.core.json.SnakePickle._

class ToolSpec extends AnyFlatSpec with Matchers {

  "Tool.Custom" should "serialize without a type discriminator" in {
    val tool = Tool.Custom(
      name = "get_weather",
      description = "Get weather for a city",
      inputSchema = ToolInputSchema.forObject(
        properties = Map("city" -> PropertySchema.string("The city name")),
        required = Some(List("city"))
      )
    )

    val json = ujson.read(write[Tool](tool))

    json.obj.contains("type") shouldBe false
    json("name").str shouldBe "get_weather"
    json("description").str shouldBe "Get weather for a city"
    json("input_schema")("type").str shouldBe "object"
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
    read[Tool](write(tool)) shouldBe tool
  }

  "Tool.WebSearch" should "serialize with type and name discriminators" in {
    val tool = Tool.WebSearch(
      maxUses = Some(5),
      allowedDomains = Some(List("example.com")),
      userLocation = Some(UserLocation.approximate(city = Some("San Francisco"), country = Some("US")))
    )

    val json = ujson.read(write[Tool](tool))

    json("type").str shouldBe "web_search_20250305"
    json("name").str shouldBe "web_search"
    json("max_uses").num shouldBe 5
    json("allowed_domains").arr.map(_.str).toList shouldBe List("example.com")
    json("user_location")("type").str shouldBe "approximate"
    json("user_location")("city").str shouldBe "San Francisco"
    json("user_location")("country").str shouldBe "US"
  }

  it should "omit unset fields" in {
    val tool: Tool = Tool.WebSearch()
    val json = ujson.read(write[Tool](tool))

    json("type").str shouldBe "web_search_20250305"
    json("name").str shouldBe "web_search"
    json.obj.contains("max_uses") shouldBe false
    json.obj.contains("allowed_domains") shouldBe false
    json.obj.contains("blocked_domains") shouldBe false
    json.obj.contains("user_location") shouldBe false
  }

  it should "round-trip" in {
    val tool: Tool = Tool.WebSearch(
      maxUses = Some(3),
      blockedDomains = Some(List("bad.example")),
      userLocation = Some(UserLocation.approximate(timezone = Some("America/Los_Angeles")))
    )
    read[Tool](write(tool)) shouldBe tool
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

    val arr = ujson.read(write(tools)).arr.toList

    arr.head.obj.contains("type") shouldBe false
    arr(1)("type").str shouldBe "web_search_20250305"

    read[List[Tool]](write(tools)) shouldBe tools
  }
}
