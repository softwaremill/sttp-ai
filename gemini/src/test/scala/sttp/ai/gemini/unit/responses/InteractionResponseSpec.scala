package sttp.ai.gemini.unit.responses

import io.circe.parser.decode
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.json.GeminiDerivedCodecs._
import sttp.ai.gemini.models._
import sttp.ai.gemini.responses.InteractionResponse

class InteractionResponseSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val responseJson =
    """{
      |  "id": "int_abc",
      |  "object": "interaction",
      |  "status": "completed",
      |  "model": "gemini-2.5-flash-lite",
      |  "steps": [
      |    {"type": "model_output", "content": [{"type": "text", "text": "It is "}, {"type": "text", "text": "sunny."}]},
      |    {"type": "function_call", "id": "call_1", "name": "get_weather", "arguments": {"city": "Warsaw"}}
      |  ],
      |  "usage": {"total_input_tokens": 10, "total_output_tokens": 5, "total_tokens": 15},
      |  "created": "2026-07-27T10:00:00Z"
      |}""".stripMargin

  "InteractionResponse" should "decode from API JSON, ignoring unknown fields like object" in {
    val response = decode[InteractionResponse](responseJson).value

    response.id shouldBe "int_abc"
    response.status shouldBe InteractionStatus.Completed
    response.model shouldBe Some("gemini-2.5-flash-lite")
    response.steps should have size 2
    response.usage.flatMap(_.totalTokens) shouldBe Some(15L)
  }

  it should "expose concatenated model output text and collected function calls" in {
    val response = decode[InteractionResponse](responseJson).value

    response.outputText shouldBe "It is sunny."
    response.functionCalls.map(_.name) shouldBe List("get_weather")
  }

  it should "decode when steps are absent" in {
    val response = decode[InteractionResponse]("""{"id":"int_x","status":"queued"}""").value
    response.steps shouldBe empty
    response.outputText shouldBe ""
  }
}
