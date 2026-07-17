package sttp.ai.claude.agent

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.Tool
import sttp.ai.core.agent.AgentTool
import sttp.apispec.Schema
import sttp.monad.IdentityMonad
import sttp.shared.Identity

class ClaudeAgentBackendSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val rawSchema =
    """{"type":"object",
      |"properties":{
      |  "title":{"type":"string"},
      |  "location":{"type":"object","properties":{"lat":{"type":"number"},"lng":{"type":"number"}},"required":["lat","lng"]}
      |},
      |"required":["title","location"]}""".stripMargin

  "ClaudeAgentBackend" should "pass the full tool schema through, preserving nested structure" in {
    val schema = parse(rawSchema).value.as[Schema](sttp.apispec.circe.schemaDecoder).value
    val tool = AgentTool.dynamic("create-event", "Creates an event", schema)(_ => "ok")

    val client = ClaudeClient(ClaudeConfig(apiKey = "test-key"))
    val backend = new ClaudeAgentBackend[Identity](client, "claude-haiku-4-5-20251001", Seq(tool), None, None)(IdentityMonad)

    backend.convertedTools.head match {
      case raw: Tool.CustomRaw =>
        raw.name shouldBe "create-event"
        raw.description shouldBe "Creates an event"
        val nested = raw.inputSchema.hcursor.downField("properties").downField("location")
        nested.downField("type").as[String] shouldBe Right("object")
        nested.downField("properties").downField("lat").downField("type").as[String] shouldBe Right("number")
        raw.inputSchema.hcursor.downField("required").as[List[String]] shouldBe Right(List("title", "location"))
      case other => fail(s"expected Tool.CustomRaw, got $other")
    }
  }

  it should "default an empty schema's input_schema to type object (Anthropic requires input_schema.type)" in {
    val schema = parse("{}").value.as[Schema](sttp.apispec.circe.schemaDecoder).value
    val tool = AgentTool.dynamic("no-arg-tool", "Takes no arguments", schema)(_ => "ok")

    val client = ClaudeClient(ClaudeConfig(apiKey = "test-key"))
    val backend = new ClaudeAgentBackend[Identity](client, "claude-haiku-4-5-20251001", Seq(tool), None, None)(IdentityMonad)

    backend.convertedTools.head match {
      case raw: Tool.CustomRaw =>
        raw.inputSchema shouldBe parse("""{"type":"object"}""").value
      case other => fail(s"expected Tool.CustomRaw, got $other")
    }
  }

  it should "pass an overridden rawJsonSchema through byte-identical, including null-valued fields" in {
    val originalSchema = parse(
      """{"type":"object","properties":{"level":{"enum":["low","high",null],"default":null}}}"""
    ).value

    val tool = new AgentTool[Identity, Map[String, Json]] {
      override def name: String = "raw-passthrough-tool"
      override def description: String = "Uses a raw schema with null-valued fields"
      override def jsonSchema: Schema = parse("""{"type":"object"}""").value.as[Schema](sttp.apispec.circe.schemaDecoder).value
      override def codec: io.circe.Codec[Map[String, Json]] = io.circe.Codec.implied
      override def execute(input: Map[String, Json]): Identity[String] = "ok"
      override def rawJsonSchema: Json = originalSchema
    }

    val client = ClaudeClient(ClaudeConfig(apiKey = "test-key"))
    val backend = new ClaudeAgentBackend[Identity](client, "claude-haiku-4-5-20251001", Seq(tool), None, None)(IdentityMonad)

    backend.convertedTools.head match {
      case raw: Tool.CustomRaw => raw.inputSchema shouldBe originalSchema
      case other               => fail(s"expected Tool.CustomRaw, got $other")
    }
  }
}
