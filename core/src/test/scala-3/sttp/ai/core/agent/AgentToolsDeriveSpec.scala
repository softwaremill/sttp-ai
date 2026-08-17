package sttp.ai.core.agent

import io.circe.Json
import io.circe.syntax.*
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.shared.Identity
import sttp.tapir.Schema.annotations.description

class AgentToolsDeriveSpec extends AnyFlatSpec with Matchers with OptionValues {

  trait WeatherService {
    @description("Get current weather")
    def currentWeather(@description("IATA city code") city: String, unit: String): String

    @description("Get a forecast")
    def forecast(city: String, days: Int): String

    private def helper(x: String): String = x
  }

  class WeatherImpl extends WeatherService {
    override def currentWeather(city: String, unit: String): String = s"weather:$city:$unit"
    override def forecast(city: String, days: Int): String = s"forecast:$city:$days"
  }

  // Decodes args with the tool's own codec, then executes — the same path the agent loop uses (cf. LoopAgent.executeTool).
  // The polymorphic T is bound from the tool's wildcard type via capture conversion, so codec and execute provably agree on T.
  private def run[T](tool: AgentTool[Identity, T], args: Json): String =
    tool.execute(tool.codec.decodeJson(args).fold(e => fail(s"decode failed: $e"), identity))

  behavior of "AgentTools.derive"

  it should "produce one tool per public method, sorted by name, with descriptions" in {
    val tools = AgentTools.derive[WeatherService](new WeatherImpl)
    tools.map(_.name) shouldBe Seq("currentWeather", "forecast")
    tools.map(_.description) shouldBe Seq("Get current weather", "Get a forecast")
  }

  it should "produce object schemas with one property per parameter, all required" in {
    val tools = AgentTools.derive[WeatherService](new WeatherImpl)
    val raw = tools.find(_.name == "forecast").value.rawJsonSchema
    raw.hcursor.downField("properties").keys.value.toSet shouldBe Set("city", "days")
    raw.hcursor.downField("properties").downField("city").get[String]("type") shouldBe Right("string")
    raw.hcursor.downField("properties").downField("days").get[String]("type") shouldBe Right("integer")
    raw.hcursor.downField("required").as[Set[String]] shouldBe Right(Set("city", "days"))
  }

  it should "decode JSON arguments and invoke the implementation" in {
    val tools = AgentTools.derive[WeatherService](new WeatherImpl)
    val forecast = tools.find(_.name == "forecast").value
    run(forecast, Json.obj("city" -> "KRK".asJson, "days" -> 3.asJson)) shouldBe "forecast:KRK:3"
    val current = tools.find(_.name == "currentWeather").value
    run(current, Json.obj("city" -> "KRK".asJson, "unit" -> "C".asJson)) shouldBe "weather:KRK:C"
  }

  it should "report a decode failure for malformed arguments instead of executing" in {
    val tools = AgentTools.derive[WeatherService](new WeatherImpl)
    val forecast = tools.find(_.name == "forecast").value
    forecast.codec.decodeJson(Json.obj("city" -> "KRK".asJson, "days" -> "three".asJson)).isLeft shouldBe true
  }

  private def codecRoundTrip[T](tool: AgentTool[Identity, T], args: Json): Json =
    tool.codec(tool.codec.decodeJson(args).toOption.value)

  it should "encode a decoded value back to an equivalent JSON object" in {
    val tools = AgentTools.derive[WeatherService](new WeatherImpl)
    val args = Json.obj("city" -> "KRK".asJson, "days" -> 3.asJson)
    codecRoundTrip(tools.find(_.name == "forecast").value, args) shouldBe args
  }
}
