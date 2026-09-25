package sttp.ai.jev.unit

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.{EitherValues, OptionValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.jev.unit.JevFixtures.*
import sttp.ai.jev.{Choice, JevClient, JevModel, Noul, Question}

enum Loud:
  case Quiet, Shouting
  override def toString: String = s"loud-$ordinal"

class JevRequestSpec extends AnyFlatSpec with Matchers with EitherValues with OptionValues:

  private def sentQuestion(question: Question[?]): Json =
    sentJson(client.askAll(state, Seq(question))).hcursor.downField("questions").downField("0").focus.value

  "JevClient" should "post questions to the systemone endpoint" in {
    client.ask(state, isUrgent).uri.toString shouldBe "https://api.typesafe.ai/v1/systemone"
  }

  it should "get the model list from the models endpoint" in {
    client.listModels().uri.toString shouldBe "https://api.typesafe.ai/v1/models"
  }

  it should "key questions by their position in the tuple" in {
    val sent = sentJson(client.ask(state, (isUrgent, department, howFrustrated)))
    sent.hcursor.downField("questions").keys.map(_.toList) shouldBe Some(List("0", "1", "2"))
  }

  it should "send the state and the configured model" in {
    val sent = sentJson(JevClient(config.copy(model = JevModel.JevPreview)).ask(state, isUrgent))
    sent.hcursor.get[String]("state").value shouldBe state
    sent.hcursor.get[String]("model").value shouldBe "jev-preview"
  }

  it should "send the API key as a bearer token" in {
    client.ask(state, isUrgent).header("Authorization") shouldBe Some("Bearer test-key")
  }

  it should "send a JSON content type" in {
    client.ask(state, isUrgent).header("Content-Type") shouldBe Some("application/json")
  }

  it should "encode a noul question with both criteria" in {
    sentQuestion(isUrgent) shouldBe parse(
      """{"type":"noul","instructions":"The message conveys urgency or time-sensitivity",
        |"criteria":{"true":"Explicitly time-sensitive","false":"No urgency expressed"}}""".stripMargin
    ).value
  }

  it should "omit noul criteria when neither side is described" in {
    sentQuestion(Noul("Is it urgent?")) shouldBe parse("""{"type":"noul","instructions":"Is it urgent?"}""").value
  }

  it should "send only the described side of noul criteria" in {
    sentQuestion(Noul("Is it urgent?", whenFalse = Some("No urgency"))).hcursor.downField("criteria").focus shouldBe
      Some(Json.obj("false" -> Json.fromString("No urgency")))
  }

  it should "encode a choice question with described options" in {
    sentQuestion(department) shouldBe parse(
      """{"type":"choice","instructions":"Which team should handle this","criteria":{"billing":"Payment or subscription issues",
        |"technical":"Bugs or integration problems","sales":"Pricing or account questions"}}""".stripMargin
    ).value
  }

  it should "send a null description for an undescribed option" in {
    sentQuestion(Choice.strings("Which name?", Seq("Beaver", "Dam"))).hcursor.downField("criteria").focus shouldBe
      Some(Json.obj("Beaver" -> Json.Null, "Dam" -> Json.Null))
  }

  it should "name enum options by their case names, not toString" in {
    sentQuestion(Choice.of[Loud]("How loud?")).hcursor.downField("criteria").keys.map(_.toList) shouldBe Some(List("Quiet", "Shouting"))
  }

  it should "encode a score question with its levels in order" in {
    sentQuestion(howFrustrated) shouldBe parse(
      """{"type":"score","instructions":"How frustrated the customer appears",
        |"criteria":["Calm, just stating facts","Frustrated but civil","Very angry"]}""".stripMargin
    ).value
  }

  it should "send structured entries verbatim" in {
    val structuredState = Json.obj("source_text" -> Json.fromString("Invoice #4471"))
    val instructions =
      Json.obj("question" -> Json.fromString("Does `extracted_value` match?"), "extracted_value" -> Json.fromString("4471"))
    val sent = sentJson(client.ask(structuredState, Noul(instructions)))
    sent.hcursor.downField("state").focus shouldBe Some(structuredState)
    sent.hcursor.downField("questions").downField("0").downField("instructions").focus shouldBe Some(instructions)
  }
