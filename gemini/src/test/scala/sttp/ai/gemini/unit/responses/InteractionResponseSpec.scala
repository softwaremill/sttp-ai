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

    response.id shouldBe Some("int_abc")
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

  // Verbatim live fixture from a real store=false Interactions API call: no `id` (stateless response), and a
  // `{"type":"thought", "signature":...}` step preceding the model_output step.
  private val liveFixture =
    """{"status":"completed","usage":{"total_tokens":23,"total_input_tokens":12,"input_tokens_by_modality":[{"modality":"text","tokens":12}],"total_cached_tokens":0,"total_output_tokens":11,"total_tool_use_tokens":0,"total_thought_tokens":0},"created":"2026-07-28T07:51:28Z","updated":"2026-07-28T07:51:28Z","service_tier":"standard","steps":[{"signature":"EjQKMgERTTIPUzlM4o4F/05UJ0vCCNg4Vd+GMV47cIlVpIevfAKyVhBjafSKil/2rhl4muYY","type":"thought"},{"content":[{"text":"{\n  \"city\": \"Paris\"\n}","type":"text"}],"type":"model_output"}],"object":"interaction","model":"gemini-3.5-flash-lite"}"""

  it should "decode a live store=false response with no id and a thought step" in {
    val response = decode[InteractionResponse](liveFixture).value

    response.status shouldBe InteractionStatus.Completed
    response.id shouldBe None
    response.outputText shouldBe "{\n  \"city\": \"Paris\"\n}"
    response.steps.collectFirst { case t: Step.Thought => t } shouldBe Some(
      Step.Thought(signature = Some("EjQKMgERTTIPUzlM4o4F/05UJ0vCCNg4Vd+GMV47cIlVpIevfAKyVhBjafSKil/2rhl4muYY"))
    )
    response.usage.flatMap(_.totalTokens) shouldBe Some(23L)
  }

  it should "decode an unrecognized step type as Step.Unknown instead of failing" in {
    val json = """{"id":"int_y","status":"completed","steps":[{"type":"some_future_step","x":1}]}"""
    val response = decode[InteractionResponse](json).value

    response.steps should have size 1
    response.steps.head shouldBe a[Step.Unknown]
  }
}
