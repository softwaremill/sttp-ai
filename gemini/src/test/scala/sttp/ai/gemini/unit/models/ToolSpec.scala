package sttp.ai.gemini.unit.models

import io.circe.parser.{decode, parse}
import io.circe.syntax._
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.gemini.json.GeminiManualCodecs._
import sttp.ai.gemini.models.Tool

class ToolSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Tool.Function" should "encode with type function and verbatim parameters" in {
    val params = parse("""{"type":"object","properties":{"level":{"enum":["low","high",null],"default":null}}}""").value
    val tool: Tool = Tool.Function("set-level", Some("Sets the level"), params)

    val json = tool.asJson
    json.hcursor.get[String]("type").value shouldBe "function"
    json.hcursor.get[String]("name").value shouldBe "set-level"
    json.hcursor.get[String]("description").value shouldBe "Sets the level"
    // parameters must be preserved verbatim, including legitimate nulls
    json.hcursor.downField("parameters").focus shouldBe Some(params)
  }

  it should "omit description when absent" in {
    val tool: Tool = Tool.Function("t", None, io.circe.Json.obj())
    tool.asJson.hcursor.downField("description").focus shouldBe None
  }

  it should "decode from JSON" in {
    val decoded = decode[Tool]("""{"type":"function","name":"t","parameters":{"type":"object"}}""").value
    decoded shouldBe Tool.Function("t", None, parse("""{"type":"object"}""").value)
  }

  "built-in tools" should "encode as bare type objects and round-trip" in {
    (Tool.GoogleSearch: Tool).asJson shouldBe parse("""{"type":"google_search"}""").value
    (Tool.CodeExecution: Tool).asJson shouldBe parse("""{"type":"code_execution"}""").value
    decode[Tool]("""{"type":"google_search"}""").value shouldBe Tool.GoogleSearch
    decode[Tool]("""{"type":"code_execution"}""").value shouldBe Tool.CodeExecution
  }
}
