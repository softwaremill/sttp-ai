package sttp.ai.core.agent

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AgentToolEnsureObjectTypeSpec extends AnyFlatSpec with Matchers {

  behavior of "AgentTool.ensureObjectType"

  it should "replace a boolean schema (MCP's `true` = any input) with a minimal object schema" in {
    AgentTool.ensureObjectType(Json.True) shouldBe parse("""{"type":"object"}""").getOrElse(fail("invalid json"))
  }

  it should "add a `type: object` field to an object schema that omits it" in {
    val schema = parse("""{"properties":{"a":{"type":"string"}}}""").getOrElse(fail("invalid json"))
    AgentTool.ensureObjectType(schema) shouldBe parse(
      """{"properties":{"a":{"type":"string"}},"type":"object"}"""
    ).getOrElse(fail("invalid json"))
  }

  it should "pass through a schema that already declares its type unchanged" in {
    val schema = parse("""{"type":"object","properties":{"a":{"type":"string"}}}""").getOrElse(fail("invalid json"))
    AgentTool.ensureObjectType(schema) shouldBe schema
  }
}
