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

  trait RichService {
    @description("Search entries")
    def search(query: String, limit: Option[Int]): String

    @description("Current time")
    def now(): String
  }

  class RichImpl extends RichService {
    override def search(query: String, limit: Option[Int]): String = s"search:$query:${limit.getOrElse(-1)}"
    override def now(): String = "now"
  }

  it should "make Option parameters non-required and decode a missing key as None" in {
    val tools = AgentTools.derive[RichService](new RichImpl)
    val search = tools.find(_.name == "search").value
    val raw = search.rawJsonSchema
    raw.hcursor.downField("properties").keys.value.toSet shouldBe Set("query", "limit")
    raw.hcursor.downField("required").as[Set[String]] shouldBe Right(Set("query"))
    run(search, Json.obj("query" -> "cats".asJson)) shouldBe "search:cats:-1"
    run(search, Json.obj("query" -> "cats".asJson, "limit" -> 5.asJson)) shouldBe "search:cats:5"
  }

  it should "attach parameter @description annotations as property descriptions" in {
    val tools = AgentTools.derive[WeatherService](new WeatherImpl)
    val raw = tools.find(_.name == "currentWeather").value.rawJsonSchema
    raw.hcursor.downField("properties").downField("city").get[String]("description") shouldBe Right("IATA city code")
    raw.hcursor.downField("properties").downField("unit").downField("description").succeeded shouldBe false
  }

  it should "support no-arg methods with an empty object schema" in {
    val tools = AgentTools.derive[RichService](new RichImpl)
    val now = tools.find(_.name == "now").value
    AgentTool.ensureObjectType(now.rawJsonSchema).hcursor.get[String]("type") shouldBe Right("object")
    run(now, Json.obj()) shouldBe "now"
  }

  trait BaseTools {
    @description("Base op")
    def baseOp(x: Int): String
  }

  trait ExtendedTools extends BaseTools {
    @description("Extended op")
    def extendedOp(y: String): String
  }

  class ExtendedImpl extends ExtendedTools {
    override def baseOp(x: Int): String = s"base:$x"
    override def extendedOp(y: String): String = s"ext:$y"
  }

  it should "include public methods inherited from parent traits" in {
    val tools = AgentTools.derive[ExtendedTools](new ExtendedImpl)
    tools.map(_.name) shouldBe Seq("baseOp", "extendedOp")
    run(tools.head, Json.obj("x" -> 42.asJson)) shouldBe "base:42"
  }

  trait EffectfulService {
    @description("Fetch by id")
    def fetch(id: Int): Option[String]

    @description("List all")
    def listAll(): Option[String]
  }

  class EffectfulImpl extends EffectfulService {
    override def fetch(id: Int): Option[String] = Some(s"fetched:$id")
    override def listAll(): Option[String] = None
  }

  private def runF[F[_], T](tool: AgentTool[F, T], args: Json): F[String] =
    tool.execute(tool.codec.decodeJson(args).fold(e => fail(s"decode failed: $e"), identity))

  it should "read @description annotations from the trait when S is inferred from an implementation class" in {
    val tools = AgentTools.derive(new WeatherImpl)
    tools.map(_.name) shouldBe Seq("currentWeather", "forecast")
    tools.map(_.description) shouldBe Seq("Get current weather", "Get a forecast")
    val raw = tools.find(_.name == "currentWeather").value.rawJsonSchema
    raw.hcursor.downField("properties").downField("city").get[String]("description") shouldBe Right("IATA city code")
  }

  trait TraitWithVar {
    var counter: Int = 0

    @description("Do the thing")
    def doThing(x: Int): String
  }

  class TraitWithVarImpl extends TraitWithVar {
    override def doThing(x: Int): String = s"did:$x"
  }

  it should "skip var accessors (getter and setter) and derive only the annotated method" in {
    val tools = AgentTools.derive[TraitWithVar](new TraitWithVarImpl)
    tools.map(_.name) shouldBe Seq("doThing")
  }

  behavior of "AgentTools.deriveF"

  it should "derive tools whose execute returns F[String]" in {
    val tools: Seq[AgentTool[Option, ?]] = AgentTools.deriveF[Option, EffectfulService](new EffectfulImpl)
    tools.map(_.name) shouldBe Seq("fetch", "listAll")
    runF(tools.find(_.name == "fetch").value, Json.obj("id" -> 7.asJson)) shouldBe Some("fetched:7")
    runF(tools.find(_.name == "listAll").value, Json.obj()) shouldBe None
  }
}
