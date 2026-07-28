package sttp.ai.gemini.unit.models

import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.json.GeminiManualCodecs._
import sttp.ai.gemini.models.InteractionStatus

class InteractionStatusSpec extends AnyFlatSpec with Matchers with EitherValues {

  "InteractionStatus" should "decode all known status values" in {
    decode[InteractionStatus]("\"completed\"").value shouldBe InteractionStatus.Completed
    decode[InteractionStatus]("\"requires_action\"").value shouldBe InteractionStatus.RequiresAction
    decode[InteractionStatus]("\"in_progress\"").value shouldBe InteractionStatus.InProgress
    decode[InteractionStatus]("\"failed\"").value shouldBe InteractionStatus.Failed
    decode[InteractionStatus]("\"cancelled\"").value shouldBe InteractionStatus.Cancelled
    decode[InteractionStatus]("\"incomplete\"").value shouldBe InteractionStatus.Incomplete
    decode[InteractionStatus]("\"budget_exceeded\"").value shouldBe InteractionStatus.BudgetExceeded
    decode[InteractionStatus]("\"queued\"").value shouldBe InteractionStatus.Queued
  }

  it should "decode an unknown status as Other instead of failing" in {
    decode[InteractionStatus]("\"some_future_status\"").value shouldBe InteractionStatus.Other("some_future_status")
  }

  it should "encode back to the raw string value" in {
    (InteractionStatus.Completed: InteractionStatus).asJson.noSpaces shouldBe "\"completed\""
    (InteractionStatus.Other("x"): InteractionStatus).asJson.noSpaces shouldBe "\"x\""
  }
}
