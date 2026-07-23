package sttp.ai.core.json

import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.json.CirceHelpers.mergeExtraBody

class CirceHelpersSpec extends AnyFlatSpec with Matchers {

  "mergeExtraBody" should "splice the object under the given field into the top level, dropping the wrapper key" in {
    val json = Json.obj(
      "model" -> Json.fromString("gpt-4o"),
      "extra_body" -> Json.obj(
        "guided_json" -> Json.obj("type" -> Json.fromString("object")),
        "top_k" -> Json.fromInt(40)
      )
    )

    val merged = mergeExtraBody("extra_body")(json)

    merged shouldBe Json.obj(
      "model" -> Json.fromString("gpt-4o"),
      "guided_json" -> Json.obj("type" -> Json.fromString("object")),
      "top_k" -> Json.fromInt(40)
    )
  }

  it should "let the extra field's entries override a colliding top-level field" in {
    val json = Json.obj(
      "temperature" -> Json.fromDoubleOrNull(0.2),
      "extra_body" -> Json.obj("temperature" -> Json.fromDoubleOrNull(0.9))
    )

    mergeExtraBody("extra_body")(json) shouldBe Json.obj("temperature" -> Json.fromDoubleOrNull(0.9))
  }

  it should "leave the JSON unchanged when the given field is absent" in {
    val json = Json.obj("model" -> Json.fromString("gpt-4o"))

    mergeExtraBody("extra_body")(json) shouldBe json
  }

  it should "leave the JSON unchanged when the given field is present but not an object" in {
    val json = Json.obj("model" -> Json.fromString("gpt-4o"), "extra_body" -> Json.Null)

    mergeExtraBody("extra_body")(json) shouldBe json
  }
}
