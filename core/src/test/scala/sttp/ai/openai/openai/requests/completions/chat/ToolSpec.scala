package sttp.openai.requests.completions.chat

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures.ToolFixture
import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.chat.message.Tool.FunctionTool

class ToolSpec extends AnyFlatSpec with Matchers with EitherValues {
  case class Passenger(name: String, age: Int)

  case class FlightDetails(passenger: Passenger, departureCity: String, destinationCity: String)

  "Given FunctionTool with schema" should "be properly serialized to Json" in {
    import sttp.tapir.generic.auto._
    // given
    val functionTool = FunctionTool.withSchema[FlightDetails](
      description = Some("Books a flight for a passenger with full details"),
      name = "book_flight"
    )
    val expectedJson = ujson.read(ToolFixture.jsonToolCall)
    // when
    val serializedToolCall = SnakePickle.writeJs(functionTool)
    // then
    serializedToolCall shouldBe expectedJson
  }

  "Given FunctionTool with strict flag" should "serialize and deserialize properly" in {
    import sttp.openai.requests.completions.chat.message.Tool.FunctionTool
    // given
    val funcTool = FunctionTool(
      description = Some("Return greeting"),
      name = "greet",
      parameters = Some(Map("type" -> ujson.Str("object"))),
      strict = Some(true)
    )

    val expectedJson = ujson.read(ToolFixture.jsonToolCallStrictTrue)

    // when
    val serialized = SnakePickle.writeJs(funcTool)
    serialized shouldBe expectedJson

    // and deserialization
    val deserialized = SnakePickle.read[FunctionTool](expectedJson)
    deserialized shouldBe funcTool
  }

  "Given FunctionTool with schema and strict flag" should "serialize and deserialize properly" in {
    import sttp.tapir.generic.auto._
    val tool = FunctionTool.withSchema[FlightDetails](
      description = Some("Books a flight for a passenger with full details"),
      name = "book_flight",
      strict = Some(true)
    )

    val expectedJson = ujson.read(ToolFixture.jsonSchematizedToolCallStrictTrue)

    val serialized = SnakePickle.writeJs(tool)
    serialized shouldBe expectedJson

    val deserialized = SnakePickle.read[FunctionTool](expectedJson)
    deserialized.strict should contain(true)
    SnakePickle.writeJs(deserialized) shouldBe expectedJson
  }

  "Given CustomTool" should "serialize properly" in {
    import sttp.openai.requests.completions.chat.message.ToolChoice.CustomTool

    // given
    val customTool = CustomTool(name = "my_custom_tool")
    val expectedJson = ujson.read(ToolFixture.jsonCustomTool)

    // when
    val serialized = SnakePickle.writeJs(customTool)

    // then
    serialized shouldBe expectedJson
  }
}
