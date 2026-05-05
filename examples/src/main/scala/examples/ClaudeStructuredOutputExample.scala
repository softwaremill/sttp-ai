package examples

import sttp.ai.claude.ClaudeSyncClient
import sttp.ai.claude.models.Message
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.json.SnakePickle
import sttp.tapir.Schema

object ClaudeStructuredOutputExample extends App {

  case class WeatherSummary(city: String, temperatureCelsius: Double, conditions: String) derives SnakePickle.ReadWriter, Schema

  val claude = ClaudeSyncClient.fromEnv
  try {
    val request = MessageRequest.simple(
      model = "claude-haiku-4-5-20251001",
      messages = List(Message.user("Summarise the weather in Krakow today.")),
      maxTokens = 200
    )
    val summary: WeatherSummary = claude.createMessageAs[WeatherSummary](request)
    println(summary)
  } finally claude.close()
}
