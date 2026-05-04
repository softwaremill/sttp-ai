package examples

import sttp.ai.claude.ClaudeSyncClient
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.Message as ClaudeMessage
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.json.SnakePickle
import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.{Content, Message}
import sttp.tapir.Schema

/** Demonstrates typed structured outputs on both OpenAI and Claude using a single Scala case class.
  *
  * The response schema is derived from the case class via Tapir; the response body is parsed back into the same case class via uPickle.
  *
  * Run from project root:
  *   - OpenAI: OPENAI_API_KEY=… sbt "examples3/runMain examples.StructuredOutputExample"
  *   - Claude: ANTHROPIC_API_KEY=… sbt "examples3/runMain examples.StructuredOutputExample"
  */
object StructuredOutputExample extends App {

  case class WeatherSummary(city: String, temperatureCelsius: Double, conditions: String) derives SnakePickle.ReadWriter, Schema

  sys.env.get("OPENAI_API_KEY").foreach { _ =>
    println("=== OpenAI ===")
    val openai = OpenAISyncClient.apply(sys.env("OPENAI_API_KEY"))
    val chatBody = ChatBody(
      model = ChatCompletionModel.GPT4oMini,
      messages = Seq(Message.UserMessage(Content.TextContent("Summarise the weather in Krakow today.")))
    )
    val summary: WeatherSummary = openai.createChatCompletionAs[WeatherSummary](chatBody)
    println(summary)
    openai.close()
  }

  sys.env.get("ANTHROPIC_API_KEY").foreach { _ =>
    println("=== Claude ===")
    val claude = ClaudeSyncClient(ClaudeConfig.fromEnv)
    val request = MessageRequest.simple(
      model = "claude-haiku-4-5-20251001",
      messages = List(ClaudeMessage.user("Summarise the weather in Krakow today.")),
      maxTokens = 200
    )
    val summary: WeatherSummary = claude.createMessageAs[WeatherSummary](request)
    println(summary)
    claude.close()
  }
}
