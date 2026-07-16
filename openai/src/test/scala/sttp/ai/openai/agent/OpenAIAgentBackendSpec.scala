package sttp.ai.openai.agent

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.agent.AgentTool
import sttp.ai.openai.OpenAI
import sttp.apispec.Schema
import sttp.monad.IdentityMonad
import sttp.shared.Identity

class OpenAIAgentBackendSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val rawSchema =
    """{"type":"object","properties":{"title":{"type":"string"},"note":{"type":"string"}},"required":["title"]}"""

  private def testTool: AgentTool[Identity, _] = {
    val schema = parse(rawSchema).value.as[Schema](sttp.apispec.circe.schemaDecoder).value
    AgentTool.dynamic("create", "Creates a thing", schema)(_ => "ok")
  }

  private def backend(strictTools: Boolean): OpenAIAgentBackend[Identity] =
    new OpenAIAgentBackend[Identity](new OpenAI("test-key"), "gpt-4o-mini", Seq(testTool), None, None, strictTools)(IdentityMonad)

  "OpenAIAgentBackend" should "register tools as strict with normalized schemas by default" in {
    val fn = new OpenAIAgentBackend[Identity](new OpenAI("test-key"), "gpt-4o-mini", Seq(testTool), None, None)(
      IdentityMonad
    ).convertedTools.head
    fn.strict shouldBe Some(true)
    val params = Json.fromFields(fn.parameters.get)
    params.hcursor.downField("additionalProperties").as[Boolean] shouldBe Right(false)
    params.hcursor.downField("required").as[List[String]] shouldBe Right(List("title", "note"))
    params.hcursor.downField("properties").downField("note").downField("type").as[List[String]] shouldBe Right(List("string", "null"))
  }

  it should "register tools as non-strict with the original schema when strictTools is false" in {
    val fn = backend(strictTools = false).convertedTools.head
    fn.strict shouldBe Some(false)
    val params = Json.fromFields(fn.parameters.get)
    params.hcursor.downField("additionalProperties").focus shouldBe None
    params.hcursor.downField("required").as[List[String]] shouldBe Right(List("title"))
    params.hcursor.downField("properties").downField("note").downField("type").as[String] shouldBe Right("string")
  }
}
