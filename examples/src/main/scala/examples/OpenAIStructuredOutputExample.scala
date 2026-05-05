package examples

import sttp.ai.core.json.SnakePickle
import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.{Content, Message}
import sttp.tapir.Schema

object OpenAIStructuredOutputExample extends App {

  case class WeatherSummary(city: String, temperatureCelsius: Double, conditions: String) derives SnakePickle.ReadWriter, Schema

  val openai = OpenAISyncClient.fromEnv
  try {
    val chatBody = ChatBody(
      model = ChatCompletionModel.GPT4oMini,
      messages = Seq(Message.UserMessage(Content.TextContent("Summarise the weather in Krakow today.")))
    )
    val summary: WeatherSummary = openai.createChatCompletionAs[WeatherSummary](chatBody)
    println(summary)
  } finally openai.close()
}
