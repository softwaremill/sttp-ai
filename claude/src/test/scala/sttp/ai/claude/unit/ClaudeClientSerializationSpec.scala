package sttp.ai.claude.unit

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{Message, PropertySchema, Tool, ToolInputSchema}
import sttp.ai.claude.requests.MessageRequest
import sttp.client4.StringBody

/** Regression coverage for the null-drop-vs-schema-fidelity fix in [[ClaudeClient]]: `deepDropNullValues` is applied to the whole request
  * at send time (to omit unset `Option` fields), but it also strips null VALUES nested inside JSON arrays, which corrupts tool schemas that
  * legitimately contain `null` (e.g. `enum` values, `default: null`). The client must preserve `input_schema` verbatim while still dropping
  * unset-field nulls everywhere else.
  */
class ClaudeClientSerializationSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val schemaWithNulls: Json = parse(
    """{"type":"object","properties":{"effort":{"type":"string","enum":["low","high",null],"default":null}},"required":["effort"]}"""
  ).value

  private def requestBodyOf(client: ClaudeClient, request: MessageRequest): String =
    client.createMessage(request).body match {
      case StringBody(s, _, _) => s
      case other               => fail(s"expected a StringBody request body, got $other")
    }

  "ClaudeClient.createMessage" should "retain nulls inside a CustomRaw tool's input_schema while dropping unset-field nulls elsewhere" in {
    val client = ClaudeClient(ClaudeConfig(apiKey = "test-key"))
    val tool: Tool = Tool.customRaw("set_effort", "Sets reasoning effort", schemaWithNulls)
    // temperature/topP/topK/stopSequences/system/outputConfig/cacheControl are all left as their `None` default, so their JSON `null`s
    // should be dropped from the serialized body -- unlike the nulls legitimately present inside the tool's input_schema.
    val request = MessageRequest
      .simple("claude-haiku-4-5-20251001", List(Message.user("hi")), 100)
      .copy(tools = Some(List(tool)))

    val json = parse(requestBodyOf(client, request)).value

    val inputSchema = json.hcursor.downField("tools").downArray.get[Json]("input_schema").value
    inputSchema shouldBe schemaWithNulls

    json.hcursor.downField("temperature").succeeded shouldBe false
    json.hcursor.downField("top_p").succeeded shouldBe false
    json.hcursor.downField("system").succeeded shouldBe false
    json.hcursor.downField("output_config").succeeded shouldBe false
  }

  it should "drop the derived-codec's null-valued fields (required/description/enum) from a Custom tool's input_schema" in {
    val client = ClaudeClient(ClaudeConfig(apiKey = "test-key"))
    val tool: Tool = Tool.Custom(
      name = "get_weather",
      description = "Gets the weather",
      // `required = None` and `PropertySchema(description = None, enum = None)` are unset `Option`s: the derived codec renders them as
      // `"required":null`/`"description":null`/`"enum":null`, which would be invalid JSON Schema if sent verbatim.
      inputSchema = ToolInputSchema(
        `type` = "object",
        properties = Map("location" -> PropertySchema(`type` = "string", description = None, `enum` = None)),
        required = None
      )
    )
    val request = MessageRequest
      .simple("claude-haiku-4-5-20251001", List(Message.user("hi")), 100)
      .copy(tools = Some(List(tool)))

    val body = requestBodyOf(client, request)
    body should not include "\"required\":null"
    body should not include "\"description\":null"
    body should not include "\"enum\":null"

    val json = parse(body).value
    val inputSchema = json.hcursor.downField("tools").downArray.get[Json]("input_schema").value
    inputSchema.asObject.exists(_.contains("required")) shouldBe false
    val locationProperty = inputSchema.hcursor.downField("properties").downField("location")
    locationProperty.downField("description").succeeded shouldBe false
    locationProperty.downField("enum").succeeded shouldBe false
  }
}
