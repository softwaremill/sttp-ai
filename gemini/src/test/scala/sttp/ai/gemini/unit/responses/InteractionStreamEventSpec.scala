package sttp.ai.gemini.unit.responses

import io.circe.parser.decode
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.json.GeminiDerivedCodecs._
import sttp.ai.gemini.responses.InteractionStreamEvent

class InteractionStreamEventSpec extends AnyFlatSpec with Matchers with EitherValues {

  "InteractionStreamEvent" should "decode a completed event with an interaction snapshot" in {
    val event = decode[InteractionStreamEvent](
      """{"event_type":"interaction.completed","event_id":"ev_1",
        |"interaction":{"id":"int_1","status":"completed","steps":[{"type":"model_output","content":[{"type":"text","text":"hi"}]}]}}""".stripMargin
    ).value

    event.eventType shouldBe "interaction.completed"
    event.interaction.flatMap(_.id) shouldBe Some("int_1")
  }

  it should "decode an error event" in {
    val event = decode[InteractionStreamEvent](
      """{"event_type":"error","error":{"code":"internal","message":"boom"}}"""
    ).value

    event.eventType shouldBe "error"
    event.error.flatMap(_.message) shouldBe Some("boom")
  }

  it should "decode an unknown event type without failing (open envelope)" in {
    val event = decode[InteractionStreamEvent]("""{"event_type":"interaction.step.delta","event_id":"ev_2"}""").value
    event.eventType shouldBe "interaction.step.delta"
    event.interaction shouldBe None
  }
}
