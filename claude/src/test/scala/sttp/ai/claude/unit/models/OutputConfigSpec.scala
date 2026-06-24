package sttp.ai.claude.unit.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models.{ContentBlock, Effort, Message, OutputConfig, OutputFormat}
import sttp.ai.claude.requests.MessageRequest
import io.circe.parser.{decode, parse}
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.OptionValues
import sttp.ai.claude.json.ClaudeDerivedCodecs._
import sttp.ai.claude.json.ClaudeManualCodecs._
import sttp.tapir.{Schema => TSchema}

class OutputConfigSpec extends AnyFlatSpec with Matchers with EitherValues with OptionValues {

  case class Person(name: String, age: Int)
  implicit val personSchema: TSchema[Person] = TSchema.derived[Person]

  val sampleMessages: List[Message] = List(Message.user(List(ContentBlock.Text("Hello"))))

  "OutputFormat.Text" should "serialize to text format" in {
    val format: OutputFormat = OutputFormat.Text
    val json = format.asJson.deepDropNullValues.noSpaces
    val parsed = parse(json).value

    parsed.hcursor.get[String]("type").value shouldBe "text"
  }

  "OutputFormat.JsonObject" should "serialize to json_object format" in {
    val format: OutputFormat = OutputFormat.JsonObject
    val json = format.asJson.deepDropNullValues.noSpaces
    val parsed = parse(json).value

    parsed.hcursor.get[String]("type").value shouldBe "json_object"
  }

  "OutputFormat.JsonSchema" should "serialize with schema" in {
    val schema = sttp.apispec.Schema(`type` = Some(List(sttp.apispec.SchemaType.Object)))
    val format: OutputFormat = OutputFormat.JsonSchema(schema)

    val json = format.asJson.deepDropNullValues.noSpaces
    val parsed = parse(json).value

    parsed.hcursor.get[String]("type").value shouldBe "json_schema"
    parsed.asObject.value.contains("schema") shouldBe true
    parsed.asObject.value.contains("name") shouldBe false
    parsed.asObject.value.contains("strict") shouldBe false
    parsed.asObject.value.contains("description") shouldBe false
  }

  it should "include additionalProperties: false for object types with properties" in {
    val format: OutputFormat = OutputFormat.JsonSchema.withTapirSchema[Person]

    val json = format.asJson.deepDropNullValues.noSpaces
    val parsed = parse(json).value

    parsed.hcursor.downField("schema").get[Boolean]("additionalProperties").value shouldBe false
  }

  "OutputFormat.JsonSchema.withTapirSchema" should "create JsonSchema with generated schema" in {
    val format = OutputFormat.JsonSchema.withTapirSchema[Person]

    format.schema should not be null
    format.schema.`type` shouldBe Some(List(sttp.apispec.SchemaType.Object))
    format.schema.properties.keys should contain allOf ("name", "age")
  }

  "OutputFormat serialization" should "round-trip correctly for Text" in {
    val original: OutputFormat = OutputFormat.Text
    val json = original.asJson.deepDropNullValues.noSpaces
    val deserialized = decode[OutputFormat](json).value

    deserialized shouldBe original
  }

  it should "round-trip correctly for JsonObject" in {
    val original: OutputFormat = OutputFormat.JsonObject
    val json = original.asJson.deepDropNullValues.noSpaces
    val deserialized = decode[OutputFormat](json).value

    deserialized shouldBe original
  }

  it should "round-trip correctly for JsonSchema" in {
    val schema = sttp.apispec.Schema(`type` = Some(List(sttp.apispec.SchemaType.Object)))
    val original: OutputFormat = OutputFormat.JsonSchema(schema)
    val json = original.asJson.deepDropNullValues.noSpaces
    val deserialized = decode[OutputFormat](json).value

    deserialized shouldBe original
  }

  "Effort" should "serialize to string values" in {
    (Effort.Low: Effort).asJson.deepDropNullValues.noSpaces shouldBe "\"low\""
    (Effort.Medium: Effort).asJson.deepDropNullValues.noSpaces shouldBe "\"medium\""
    (Effort.High: Effort).asJson.deepDropNullValues.noSpaces shouldBe "\"high\""
    (Effort.Max: Effort).asJson.deepDropNullValues.noSpaces shouldBe "\"max\""
  }

  it should "round-trip correctly for all values" in
    List(Effort.Low, Effort.Medium, Effort.High, Effort.Max).foreach { effort =>
      val json = (effort: Effort).asJson.deepDropNullValues.noSpaces
      val deserialized = decode[Effort](json).value
      deserialized shouldBe effort
    }

  "OutputConfig" should "serialize with format only" in {
    val config = OutputConfig(format = Some(OutputFormat.Text))
    val json = config.asJson.deepDropNullValues.noSpaces
    val parsed = parse(json).value

    parsed.hcursor.downField("format").get[String]("type").value shouldBe "text"
    parsed.asObject.value.contains("effort") shouldBe false
  }

  it should "serialize with effort only" in {
    val config = OutputConfig(effort = Some(Effort.High))
    val json = config.asJson.deepDropNullValues.noSpaces
    val parsed = parse(json).value

    parsed.hcursor.get[String]("effort").value shouldBe "high"
    parsed.asObject.value.contains("format") shouldBe false
  }

  it should "serialize with both format and effort" in {
    val config = OutputConfig(format = Some(OutputFormat.JsonObject), effort = Some(Effort.Max))
    val json = config.asJson.deepDropNullValues.noSpaces
    val parsed = parse(json).value

    parsed.hcursor.downField("format").get[String]("type").value shouldBe "json_object"
    parsed.hcursor.get[String]("effort").value shouldBe "max"
  }

  it should "round-trip correctly" in {
    val original = OutputConfig(format = Some(OutputFormat.Text), effort = Some(Effort.Medium))
    val json = original.asJson.deepDropNullValues.noSpaces
    val deserialized = decode[OutputConfig](json).value

    deserialized shouldBe original
  }

  "usesStructuredOutput detection" should "return false for Text and JsonObject" in {
    val textRequest = MessageRequest(
      model = "claude-sonnet-4-5-20250514",
      messages = sampleMessages,
      maxTokens = 1024,
      outputConfig = Some(OutputConfig(format = Some(OutputFormat.Text)))
    )
    textRequest.usesStructuredOutput shouldBe false

    val jsonObjectRequest = MessageRequest(
      model = "claude-sonnet-4-5-20250514",
      messages = sampleMessages,
      maxTokens = 1024,
      outputConfig = Some(OutputConfig(format = Some(OutputFormat.JsonObject)))
    )
    jsonObjectRequest.usesStructuredOutput shouldBe false
  }

  it should "return true for JsonSchema" in {
    val schema = sttp.apispec.Schema(`type` = Some(List(sttp.apispec.SchemaType.Object)))
    val jsonSchemaFormat = OutputFormat.JsonSchema(schema)

    val request = MessageRequest
      .simple("claude-sonnet-4-5-20250514", sampleMessages, 1024)
      .withStructuredOutput(jsonSchemaFormat)
    request.usesStructuredOutput shouldBe true
  }

  it should "return false when no outputConfig is provided" in {
    val request = MessageRequest.simple("claude-sonnet-4-5-20250514", sampleMessages, 1024)
    request.usesStructuredOutput shouldBe false
  }
}
