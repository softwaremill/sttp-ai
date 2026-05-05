//> using repository ivy2Local
//> using dep com.softwaremill.sttp.ai::claude:0.4.0
//> using dep ch.qos.logback:logback-classic:1.5.19

// remember to set the ANTHROPIC_API_KEY env variable!
// run with: ANTHROPIC_API_KEY=... scala-cli run ClaudeStructuredOutputExample.scala

package examples

import sttp.ai.claude.ClaudeSyncClient
import sttp.ai.claude.models.Message
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.core.json.SnakePickle
import sttp.tapir.Schema

object ClaudeStructuredOutputExample extends App {

  case class Language(name: String, paradigm: String, summary: String) derives SnakePickle.ReadWriter, Schema

  case class LanguageList(languages: List[Language]) derives SnakePickle.ReadWriter, Schema

  val claude = ClaudeSyncClient.fromEnv
  try {
    val request = MessageRequest.simple(
      model = "claude-haiku-4-5-20251001",
      messages = List(
        Message.user(
          "List 10 well-known programming languages. For each, give the dominant paradigm and a one-sentence summary."
        )
      ),
      maxTokens = 1500
    )
    val result: LanguageList = claude.createMessageAs[LanguageList](request)
    result.languages.foreach { l =>
      println(s"${l.name} [${l.paradigm}] — ${l.summary}")
    }
  } finally claude.close()
}
