package sttp.ai.claude.unit.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models.OutputFormat
import sttp.ai.core.json.SnakePickle
import sttp.tapir.{Schema => TSchema}

class OutputFormatSpec extends AnyFlatSpec with Matchers {

  case class Person(name: String, age: Int)
  implicit val personSchema: TSchema[Person] = TSchema.derived[Person]

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

  "usesStructuredOutput detection" should "return false for Text and JsonObject" in {
    import sttp.ai.claude.requests.MessageRequest
    import sttp.ai.claude.models.{ContentBlock, Message}

    val sampleMessages = List(Message.user(List(ContentBlock.TextContent("Hello"))))

    val textRequest = MessageRequest.simple("claude-sonnet-4-5-20250514", sampleMessages, 1024, Some(OutputFormat.Text))
    textRequest.usesStructuredOutput shouldBe false

    val jsonObjectRequest = MessageRequest.simple("claude-sonnet-4-5-20250514", sampleMessages, 1024, Some(OutputFormat.JsonObject))
    jsonObjectRequest.usesStructuredOutput shouldBe false
  }

  it should "return true for JsonSchema" in {
    import sttp.ai.claude.requests.MessageRequest
    import sttp.ai.claude.models.{ContentBlock, Message}

    val sampleMessages = List(Message.user(List(ContentBlock.TextContent("Hello"))))
    val schema = sttp.apispec.Schema(`type` = Some(List(sttp.apispec.SchemaType.Object)))
    val jsonSchemaFormat = OutputFormat.JsonSchema(schema)

    val request = MessageRequest.simple("claude-sonnet-4-5-20250514", sampleMessages, 1024, Some(jsonSchemaFormat))
    request.usesStructuredOutput shouldBe true
  }

  it should "return false when no outputFormat is provided" in {
    import sttp.ai.claude.requests.MessageRequest
    import sttp.ai.claude.models.{ContentBlock, Message}

    val sampleMessages = List(Message.user(List(ContentBlock.TextContent("Hello"))))

    val request = MessageRequest.simple("claude-sonnet-4-5-20250514", sampleMessages, 1024, None)
    request.usesStructuredOutput shouldBe false
  }
}
