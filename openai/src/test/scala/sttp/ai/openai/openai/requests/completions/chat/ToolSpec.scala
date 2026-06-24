package sttp.ai.openai.requests.completions.chat

import io.circe.parser.parse
import io.circe.syntax._
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.json.OpenAIManualCodecs._

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.fixtures.ToolFixture
import sttp.ai.openai.requests.completions.chat.message.Tool
import sttp.ai.openai.requests.completions.chat.message.Tool.Function

class ToolSpec extends AnyFlatSpec with Matchers with EitherValues {
  case class Passenger(name: String, age: Int)

  case class FlightDetails(passenger: Passenger, departureCity: String, destinationCity: String)

  "Given FunctionTool with schema" should "be properly serialized to Json" in {
    import sttp.tapir.generic.auto._
    // given
    val functionTool = Function.withSchema[FlightDetails](
      description = Some("Books a flight for a passenger with full details"),
      name = "book_flight"
    )
    val expectedJson = parse(ToolFixture.jsonToolCall).value
    // when
    val serializedToolCall = (functionTool: Tool).asJson.deepDropNullValues
    // then
    serializedToolCall shouldBe expectedJson.deepDropNullValues
  }

  "Given FunctionTool with strict flag" should "serialize and deserialize properly" in {
    import sttp.ai.openai.requests.completions.chat.message.Tool.Function
    // given
    val funcTool = Function(
      description = Some("Return greeting"),
      name = "greet",
      parameters = Some(Map("type" := "object")),
      strict = Some(true)
    )

    val expectedJson = parse(ToolFixture.jsonToolCallStrictTrue).value

    // when
    val serialized = (funcTool: Tool).asJson.deepDropNullValues
    serialized shouldBe expectedJson.deepDropNullValues

    // and deserialization
    val deserialized = expectedJson.as[Tool].value
    deserialized shouldBe funcTool
  }

  "Given FunctionTool with schema and strict flag" should "serialize and deserialize properly" in {
    import sttp.tapir.generic.auto._
    val tool = Function.withSchema[FlightDetails](
      description = Some("Books a flight for a passenger with full details"),
      name = "book_flight",
      strict = Some(true)
    )

    val expectedJson = parse(ToolFixture.jsonSchematizedToolCallStrictTrue).value

    val serialized = (tool: Tool).asJson.deepDropNullValues
    serialized shouldBe expectedJson.deepDropNullValues

    val deserialized = expectedJson.as[Tool].value
    deserialized match {
      case f: Function => f.strict should contain(true)
      case other       => fail(s"expected a function tool, got $other")
    }
    deserialized.asJson.deepDropNullValues shouldBe expectedJson.deepDropNullValues
  }

  "Given CustomTool" should "serialize properly" in {
    import sttp.ai.openai.requests.completions.chat.message.ToolChoice.Custom

    // given
    val customTool: sttp.ai.openai.requests.completions.chat.message.ToolChoice = Custom(name = "my_custom_tool")
    val expectedJson = parse(ToolFixture.jsonCustomTool).value

    // when
    val serialized = customTool.asJson.deepDropNullValues

    // then
    serialized shouldBe expectedJson.deepDropNullValues
  }
}
