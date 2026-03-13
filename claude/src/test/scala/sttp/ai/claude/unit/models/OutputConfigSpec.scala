package sttp.ai.claude.unit.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models.{ContentBlock, Effort, Message, OutputConfig, OutputFormat}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.json.SnakePickle
import sttp.tapir.{Schema => TSchema}

class OutputConfigSpec extends AnyFlatSpec with Matchers {

  case class Person(name: String, age: Int)
  implicit val personSchema: TSchema[Person] = TSchema.derived[Person]

  val sampleMessages: List[Message] = List(Message.user(List(ContentBlock.TextContent("Hello"))))

  "OutputFormat.Text" should "serialize to text format" in {
    val format: OutputFormat = OutputFormat.Text
    val json = SnakePickle.write(format)
    val parsed = ujson.read(json)

    parsed("type").str shouldBe "text"
  }

  "OutputFormat.JsonObject" should "serialize to json_object format" in {
    val format: OutputFormat = OutputFormat.JsonObject
    val json = SnakePickle.write(format)
    val parsed = ujson.read(json)

    parsed("type").str shouldBe "json_object"
  }

  "OutputFormat.JsonSchema" should "serialize with schema" in {
    val schema = sttp.apispec.Schema(`type` = Some(List(sttp.apispec.SchemaType.Object)))
    val format = OutputFormat.JsonSchema(schema)

    val json = SnakePickle.write(format)
    val parsed = ujson.read(json)

    parsed("type").str shouldBe "json_schema"
    parsed.obj.contains("schema") shouldBe true
    parsed.obj.contains("name") shouldBe false
    parsed.obj.contains("strict") shouldBe false
    parsed.obj.contains("description") shouldBe false
  }

  it should "include additionalProperties: false for object types with properties" in {
    val format = OutputFormat.JsonSchema.withTapirSchema[Person]

    val json = SnakePickle.write(format)
    val parsed = ujson.read(json)

    parsed("schema")("additionalProperties").bool shouldBe false
  }

  "OutputFormat.JsonSchema.withTapirSchema" should "create JsonSchema with generated schema" in {
    val format = OutputFormat.JsonSchema.withTapirSchema[Person]

    format.schema should not be null
    format.schema.`type` shouldBe Some(List(sttp.apispec.SchemaType.Object))
    format.schema.properties.keys should contain allOf ("name", "age")
  }

  "OutputFormat serialization" should "round-trip correctly for Text" in {
    val original: OutputFormat = OutputFormat.Text
    val json = SnakePickle.write(original)
    val deserialized = SnakePickle.read[OutputFormat](json)

    deserialized shouldBe original
  }

  it should "round-trip correctly for JsonObject" in {
    val original: OutputFormat = OutputFormat.JsonObject
    val json = SnakePickle.write(original)
    val deserialized = SnakePickle.read[OutputFormat](json)

    deserialized shouldBe original
  }

  it should "round-trip correctly for JsonSchema" in {
    val schema = sttp.apispec.Schema(`type` = Some(List(sttp.apispec.SchemaType.Object)))
    val original = OutputFormat.JsonSchema(schema)
    val json = SnakePickle.write(original)
    val deserialized = SnakePickle.read[OutputFormat](json)

    deserialized shouldBe original
  }

  "Effort" should "serialize to string values" in {
    SnakePickle.write(Effort.Low: Effort) shouldBe "\"low\""
    SnakePickle.write(Effort.Medium: Effort) shouldBe "\"medium\""
    SnakePickle.write(Effort.High: Effort) shouldBe "\"high\""
    SnakePickle.write(Effort.Max: Effort) shouldBe "\"max\""
  }

  it should "round-trip correctly for all values" in
    List(Effort.Low, Effort.Medium, Effort.High, Effort.Max).foreach { effort =>
      val json = SnakePickle.write(effort: Effort)
      val deserialized = SnakePickle.read[Effort](json)
      deserialized shouldBe effort
    }

  "OutputConfig" should "serialize with format only" in {
    val config = OutputConfig(format = Some(OutputFormat.Text))
    val json = SnakePickle.write(config)
    val parsed = ujson.read(json)

    parsed("format")("type").str shouldBe "text"
    parsed.obj.contains("effort") shouldBe false
  }

  it should "serialize with effort only" in {
    val config = OutputConfig(effort = Some(Effort.High))
    val json = SnakePickle.write(config)
    val parsed = ujson.read(json)

    parsed("effort").str shouldBe "high"
    parsed.obj.contains("format") shouldBe false
  }

  it should "serialize with both format and effort" in {
    val config = OutputConfig(format = Some(OutputFormat.JsonObject), effort = Some(Effort.Max))
    val json = SnakePickle.write(config)
    val parsed = ujson.read(json)

    parsed("format")("type").str shouldBe "json_object"
    parsed("effort").str shouldBe "max"
  }

  it should "round-trip correctly" in {
    val original = OutputConfig(format = Some(OutputFormat.Text), effort = Some(Effort.Medium))
    val json = SnakePickle.write(original)
    val deserialized = SnakePickle.read[OutputConfig](json)

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
