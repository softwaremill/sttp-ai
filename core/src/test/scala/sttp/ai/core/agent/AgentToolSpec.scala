package sttp.ai.core.agent

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema

class AgentToolSpec extends AnyFlatSpec with Matchers with OptionValues {

  case class CalculatorInput(a: Double, b: Double)
  implicit val calculatorInputCodec: Codec[CalculatorInput] = deriveCodec
  implicit val calculatorInputSchema: Schema[CalculatorInput] = Schema.derived

  private val calculatorTool = AgentTool.fromFunction(
    "calculator",
    "Calculate"
  ) { (input: CalculatorInput) =>
    s"Result: ${input.a + input.b}"
  }

  behavior of "AgentTool.rawJsonSchema"

  it should "default to the faithful, null-free encoding of jsonSchema" in {
    val raw = calculatorTool.rawJsonSchema
    val expected = sttp.apispec.circe.encoderSchema(calculatorTool.jsonSchema).deepDropNullValues
    raw shouldBe expected

    val cursor = raw.hcursor.downField("properties")
    cursor.keys.value.toSet shouldBe Set("a", "b")

    def hasNoNulls(json: io.circe.Json): Boolean = json.fold(
      jsonNull = false,
      jsonBoolean = _ => true,
      jsonNumber = _ => true,
      jsonString = _ => true,
      jsonArray = _.forall(hasNoNulls),
      jsonObject = _.values.forall(hasNoNulls)
    )
    hasNoNulls(raw) shouldBe true
  }
}
