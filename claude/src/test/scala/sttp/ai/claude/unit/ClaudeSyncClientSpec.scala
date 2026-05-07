package sttp.ai.claude.unit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.claude.ClaudeExceptions.ClaudeException.DeserializationClaudeException
import sttp.ai.claude.ClaudeSyncClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{Message, OutputFormat}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.json.SnakePickle
import sttp.client4.DefaultSyncBackend
import sttp.model.StatusCode

class ClaudeSyncClientSpec extends AnyFlatSpec with Matchers {

  case class Weather(city: String, tempC: Double, conditions: String)
  object Weather {
    implicit val rw: SnakePickle.ReadWriter[Weather] = SnakePickle.macroRW
  }

  private val structuredResponse =
    """{
      |  "id": "msg_1",
      |  "type": "message",
      |  "role": "assistant",
      |  "content": [
      |    {"type": "text", "text": "{\"city\":\"Krakow\",\"temp_c\":12.0,\"conditions\":\"sunny\"}"}
      |  ],
      |  "model": "claude-haiku-4-5-20251001",
      |  "stop_reason": "end_turn",
      |  "usage": {"input_tokens": 10, "output_tokens": 20}
      |}""".stripMargin

  private val unparseableContentResponse =
    """{
      |  "id": "msg_1",
      |  "type": "message",
      |  "role": "assistant",
      |  "content": [
      |    {"type": "text", "text": "not-json"}
      |  ],
      |  "model": "claude-haiku-4-5-20251001",
      |  "stop_reason": "end_turn",
      |  "usage": {"input_tokens": 10, "output_tokens": 20}
      |}""".stripMargin

  private def request: MessageRequest =
    MessageRequest.simple(
      model = "claude-haiku-4-5-20251001",
      messages = List(Message.user("Summarise the weather in Krakow.")),
      maxTokens = 200
    )

  private def stubClient(body: String): ClaudeSyncClient = {
    val backend = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(body, StatusCode.Ok)
    val config = ClaudeConfig(apiKey = "test-key")
    ClaudeSyncClient(config, backend)
  }

  "createMessageAs" should "parse the response into the typed value via uPickle" in {
    val client = stubClient(structuredResponse)

    import sttp.tapir.generic.auto._
    val res: Weather = client.createMessageAs[Weather](request)

    res shouldBe Weather("Krakow", 12.0, "sunny")
  }

  it should "throw a DeserializationClaudeException when the text content is not valid JSON for T" in {
    val client = stubClient(unparseableContentResponse)

    import sttp.tapir.generic.auto._
    intercept[DeserializationClaudeException](client.createMessageAs[Weather](request)): Unit
  }

  it should "respect a structured output format already set on the request" in {
    import sttp.tapir.generic.auto._

    val client = stubClient(structuredResponse)
    val withFmt = request.withStructuredOutput(OutputFormat.JsonSchema.withTapirSchema[Weather])
    withFmt.usesStructuredOutput shouldBe true: Unit

    val res: Weather = client.createMessageAs[Weather](withFmt)
    res shouldBe Weather("Krakow", 12.0, "sunny")
  }
}
