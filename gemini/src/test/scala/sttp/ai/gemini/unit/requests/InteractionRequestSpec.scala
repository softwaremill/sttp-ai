package sttp.ai.gemini.unit.requests

import io.circe.parser.{decode, parse}
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.json.GeminiDerivedCodecs._
import sttp.ai.gemini.models._
import sttp.ai.gemini.requests.InteractionRequest

class InteractionRequestSpec extends AnyFlatSpec with Matchers with EitherValues {

  "InteractionInput" should "encode a text input as a plain JSON string" in {
    (InteractionInput.TextInput("hello"): InteractionInput).asJson shouldBe io.circe.Json.fromString("hello")
  }

  it should "encode a steps input as a JSON array of discriminated steps" in {
    val input: InteractionInput = InteractionInput.StepsInput(
      List(
        Step.userText("hi"),
        Step.FunctionCall("call_1", "get_weather", parse("""{"city":"Warsaw"}""").value),
        Step.FunctionResult("call_1", "get_weather", io.circe.Json.fromString("sunny"))
      )
    )
    val json = input.asJson.deepDropNullValues
    json shouldBe parse(
      """[
        |{"type":"user_input","content":[{"type":"text","text":"hi"}]},
        |{"type":"function_call","id":"call_1","name":"get_weather","arguments":{"city":"Warsaw"}},
        |{"type":"function_result","call_id":"call_1","name":"get_weather","result":"sunny"}
        |]""".stripMargin
    ).value
  }

  it should "decode a string back to TextInput and an array back to StepsInput" in {
    decode[InteractionInput]("\"hello\"").value shouldBe InteractionInput.TextInput("hello")
    decode[InteractionInput]("""[{"type":"user_input","content":[{"type":"text","text":"hi"}]}]""").value shouldBe
      InteractionInput.StepsInput(List(Step.UserInput(List(Content.Text("hi")))))
  }

  "InteractionRequest" should "serialize with snake_case fields" in {
    val request = InteractionRequest
      .simple("gemini-2.5-flash-lite", "hello")
      .copy(
        systemInstruction = Some("be brief"),
        previousInteractionId = Some("int_123"),
        store = Some(false)
      )
    val json = request.asJson.deepDropNullValues
    json shouldBe parse(
      """{"model":"gemini-2.5-flash-lite","input":"hello","system_instruction":"be brief",
        |"previous_interaction_id":"int_123","store":false}""".stripMargin
    ).value
  }
}
