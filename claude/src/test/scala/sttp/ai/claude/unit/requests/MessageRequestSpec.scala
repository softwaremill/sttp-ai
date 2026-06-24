package sttp.ai.claude.unit.requests

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.models.{CacheControl, ContentBlock, Message, OutputFormat}
import sttp.ai.claude.requests.MessageRequest
import io.circe.parser.{decode, parse}
import io.circe.Json
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.OptionValues
import sttp.ai.claude.json.ClaudeDerivedCodecs._
import sttp.ai.claude.json.ClaudeManualCodecs._
import sttp.tapir.{Schema => TSchema}

class MessageRequestSpec extends AnyFlatSpec with Matchers with EitherValues with OptionValues {

  case class UserProfile(
      name: String,
      age: Int,
      email: String,
      isActive: Boolean,
      tags: List[String]
  )

  implicit val userProfileSchema: TSchema[UserProfile] = TSchema.derived[UserProfile]

  val sampleMessages: List[Message] = List(
    Message.user(List(ContentBlock.Text("Hello")))
  )

  // not ttl test
  val sampleMessagesWithCacheControl: List[Message] = List(
    Message.user(List(ContentBlock.Text("Hello", cacheControl = Some(CacheControl.Ephemeral()))))
  )

  "MessageRequest serialization" should "include output_config with format and schema" in {
    val outputFormat = OutputFormat.JsonSchema.withTapirSchema[UserProfile]
    val request = MessageRequest
      .simple("claude-sonnet-4-5-20250514", sampleMessages, 1024)
      .withStructuredOutput(outputFormat)

    val json = request.asJson.deepDropNullValues.noSpaces
    val parsed = parse(json).value

    parsed.hcursor.get[String]("model").value shouldBe "claude-sonnet-4-5-20250514"
    parsed.hcursor.get[Int]("max_tokens").value shouldBe 1024
    parsed.hcursor.downField("output_config").downField("format").get[String]("type").value shouldBe "json_schema"

    val schema = parsed.hcursor.downField("output_config").downField("format").get[Json]("schema").value
    schema.hcursor.get[String]("type").value shouldBe "object"
    schema.hcursor.downField("properties").focus.value.asObject.value.contains("name") shouldBe true
    schema.hcursor.downField("properties").focus.value.asObject.value.contains("age") shouldBe true
    schema.hcursor.downField("properties").focus.value.asObject.value.contains("email") shouldBe true
    schema.hcursor.downField("properties").focus.value.asObject.value.contains("isActive") shouldBe true
    schema.hcursor.downField("properties").focus.value.asObject.value.contains("tags") shouldBe true
  }

  it should "not include output_config when absent" in {
    val request = MessageRequest.simple("claude-sonnet-4-5-20250514", sampleMessages, 1024)

    val json = request.asJson.deepDropNullValues.noSpaces
    val parsed = parse(json).value

    parsed.asObject.value.contains("output_config") shouldBe false
  }

  it should "round-trip with structured output" in {
    val outputFormat = OutputFormat.JsonSchema.withTapirSchema[UserProfile]
    val request = MessageRequest
      .simple("claude-sonnet-4-5-20250514", sampleMessages, 1024)
      .withStructuredOutput(outputFormat)

    val json = request.asJson.deepDropNullValues.noSpaces
    val deserialized = decode[MessageRequest](json).value

    deserialized.model shouldBe request.model
    deserialized.maxTokens shouldBe request.maxTokens
    deserialized.outputConfig shouldBe defined
    deserialized.outputConfig.get.format shouldBe defined
    deserialized.outputConfig.get.format.get shouldBe a[OutputFormat.JsonSchema]
  }

  it should "round-trip with caching control" in {
    val request = MessageRequest
      .simple("claude-sonnet-4-5-20250514", sampleMessagesWithCacheControl, 1024)
      .withCacheControl(CacheControl.Ephemeral())

    val json = request.asJson.deepDropNullValues.noSpaces
    val deserialized = decode[MessageRequest](json).value
    deserialized shouldEqual request
  }
}
