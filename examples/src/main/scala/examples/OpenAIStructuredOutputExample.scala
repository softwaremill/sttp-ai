//> using repository ivy2Local
//> using dep com.softwaremill.sttp.ai::openai:0.4.0
//> using dep ch.qos.logback:logback-classic:1.5.19

// remember to set the OPENAI_API_KEY env variable!
// run with: OPENAI_API_KEY=... scala-cli run OpenAIStructuredOutputExample.scala

package examples

import sttp.ai.core.json.SnakePickle
import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.{Content, Message}
import sttp.tapir.Schema

object OpenAIStructuredOutputExample extends App {

  case class Language(name: String, paradigm: String, summary: String) derives SnakePickle.ReadWriter, Schema

  case class LanguageList(languages: List[Language]) derives SnakePickle.ReadWriter, Schema

  val openai = OpenAISyncClient.fromEnv
  try {
    val chatBody = ChatBody(
      model = ChatCompletionModel.GPT4oMini,
      messages = Seq(
        Message.UserMessage(
          Content.TextContent(
            "List 10 well-known programming languages. For each, give the dominant paradigm and a one-sentence summary."
          )
        )
      )
    )
    val result: LanguageList = openai.createChatCompletionAs[LanguageList](chatBody)
    result.languages.foreach { l =>
      println(s"${l.name} [${l.paradigm}] — ${l.summary}")
    }
  } finally openai.close()
}
