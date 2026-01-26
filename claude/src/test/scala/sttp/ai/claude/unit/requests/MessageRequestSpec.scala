package sttp.ai.claude.unit.requests

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models.{ContentBlock, Message, OutputFormat}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.json.SnakePickle
import sttp.tapir.{Schema => TSchema}
import ujson._

class MessageRequestSpec extends AnyFlatSpec with Matchers {

  case class UserProfile(
      name: String,
      age: Int,
      email: String,
      isActive: Boolean,
      tags: List[String]
  )

  implicit val userProfileSchema: TSchema[UserProfile] = TSchema.derived[UserProfile]

  val sampleMessages: List[Message] = List(
    Message.user(List(ContentBlock.TextContent("Hello")))
  )

  "MessageRequest serialization" should "include output_format with schema" in {
    val outputFormat = OutputFormat.JsonSchema.withTapirSchema[UserProfile]
    val request = MessageRequest
      .simple("claude-sonnet-4-5-20250514", sampleMessages, 1024)
      .withStructuredOutput(outputFormat)

    val json = SnakePickle.write(request)
    val parsed = ujson.read(json)

    parsed("model").str shouldBe "claude-sonnet-4-5-20250514"
    parsed("max_tokens").num shouldBe 1024
    parsed("output_format")("type").str shouldBe "json_schema"

    val schema = parsed("output_format")("schema")
    schema("type").str shouldBe "object"
    schema("properties").obj.contains("name") shouldBe true
    schema("properties").obj.contains("age") shouldBe true
    schema("properties").obj.contains("email") shouldBe true
    schema("properties").obj.contains("isActive") shouldBe true
    schema("properties").obj.contains("tags") shouldBe true
  }

  it should "not include output_format when absent" in {
    val request = MessageRequest.simple("claude-sonnet-4-5-20250514", sampleMessages, 1024)

    val json = SnakePickle.write(request)
    val parsed = ujson.read(json)

    parsed.obj.contains("output_format") shouldBe false
  }

  it should "round-trip with structured output" in {
    val outputFormat = OutputFormat.JsonSchema.withTapirSchema[UserProfile]
    val request = MessageRequest
      .simple("claude-sonnet-4-5-20250514", sampleMessages, 1024)
      .withStructuredOutput(outputFormat)

    val json = SnakePickle.write(request)
    val deserialized = SnakePickle.read[MessageRequest](json)

    deserialized.model shouldBe request.model
    deserialized.maxTokens shouldBe request.maxTokens
    deserialized.outputFormat shouldBe defined
    deserialized.outputFormat.get shouldBe a[OutputFormat.JsonSchema]
  }
}
