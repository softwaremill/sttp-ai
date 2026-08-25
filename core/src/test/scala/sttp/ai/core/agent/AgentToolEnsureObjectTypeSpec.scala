package sttp.ai.core.agent

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AgentToolEnsureObjectTypeSpec extends AnyFlatSpec with Matchers {

  behavior of "AgentTool.ensureObjectType"

  it should "replace a boolean schema (MCP's `true` = any input) with a minimal object schema" in {
    AgentTool.ensureObjectType(Json.True) shouldBe parse("""{"type":"object","properties":{}}""").getOrElse(fail("invalid json"))
  }

  it should "add empty properties to a propertyless object schema" in {
    val schema = parse("""{"type":"object"}""").getOrElse(fail("invalid json"))
    AgentTool.ensureObjectType(schema) shouldBe parse("""{"type":"object","properties":{}}""").getOrElse(fail("invalid json"))
  }

  it should "not inject properties next to a $ref, combinator, or discriminator" in {
    val refRoot = parse("""{"type":"object","$ref":"#/$defs/X"}""").getOrElse(fail("invalid json"))
    AgentTool.ensureObjectType(refRoot) shouldBe refRoot
    val anyOfRoot = parse("""{"type":"object","anyOf":[{"type":"string"}]}""").getOrElse(fail("invalid json"))
    AgentTool.ensureObjectType(anyOfRoot) shouldBe anyOfRoot
    val discRoot = parse("""{"type":"object","discriminator":{"propertyName":"kind"},"oneOf":[]}""").getOrElse(fail("invalid json"))
    AgentTool.ensureObjectType(discRoot) shouldBe discRoot
  }

  it should "not inject properties into a map-like object with schema-form or true additionalProperties" in {
    val mapLike = parse("""{"type":"object","additionalProperties":{"type":"string"}}""").getOrElse(fail("invalid json"))
    AgentTool.ensureObjectType(mapLike) shouldBe mapLike
    val freeForm = parse("""{"type":"object","additionalProperties":true}""").getOrElse(fail("invalid json"))
    AgentTool.ensureObjectType(freeForm) shouldBe freeForm
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
