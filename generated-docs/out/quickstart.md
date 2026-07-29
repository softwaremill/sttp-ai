# Quickstart

## For OpenAI/OpenAI-compatible APIs

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "openai" % "0.5.6"

// For streaming support, add one or more (these modules are shared across OpenAI, Claude, and Gemini):
"com.softwaremill.sttp.ai" %% "fs2" % "0.5.6"    // cats-effect/fs2
"com.softwaremill.sttp.ai" %% "zio" % "0.5.6"    // ZIO
"com.softwaremill.sttp.ai" %% "akka" % "0.5.6"   // Akka Streams (Scala 2.13 only)
"com.softwaremill.sttp.ai" %% "pekko" % "0.5.6"  // Pekko Streams
"com.softwaremill.sttp.ai" %% "ox" % "0.5.6"     // Ox direct-style (Scala 3 only)
```

Then send your first request (reads the `OPENAI_KEY` environment variable):

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.5.6

import sttp.ai.openai.OpenAISyncClient
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel}
import sttp.ai.openai.requests.completions.chat.message.*

object OpenAIHello:
  def main(args: Array[String]): Unit =
    val openAI = OpenAISyncClient(System.getenv("OPENAI_KEY"))
    try {
      val chatBody = ChatBody(
        model = ChatCompletionModel.GPT4oMini,
        messages = Seq(Message.User(Content.TextContent("Hello!")))
      )
      println(openAI.createChatCompletion(chatBody).choices.head.message.content)
    } finally openAI.close()
```

See [OpenAI API basics](openai/basics.md) for more.

## For Claude (Anthropic) API

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "claude" % "0.5.6"

// For streaming support, add one or more (these modules are shared across OpenAI, Claude, and Gemini):
"com.softwaremill.sttp.ai" %% "fs2" % "0.5.6"    // cats-effect/fs2
"com.softwaremill.sttp.ai" %% "zio" % "0.5.6"    // ZIO
"com.softwaremill.sttp.ai" %% "akka" % "0.5.6"   // Akka Streams (Scala 2.13 only)
"com.softwaremill.sttp.ai" %% "pekko" % "0.5.6"  // Pekko Streams
"com.softwaremill.sttp.ai" %% "ox" % "0.5.6"     // Ox direct-style (Scala 3 only)
```

Then send your first request (reads the `ANTHROPIC_API_KEY` environment variable):

```scala
//> using dep com.softwaremill.sttp.ai::claude:0.5.6

import sttp.ai.claude.ClaudeSyncClient
import sttp.ai.claude.models.{ClaudeModel, Message}
import sttp.ai.claude.requests.MessageRequest

object ClaudeHello:
  def main(args: Array[String]): Unit =
    val claude = ClaudeSyncClient.fromEnv
    try {
      val request = MessageRequest.simple(
        model = ClaudeModel.ClaudeHaiku4_5.value,
        messages = List(Message.user("Hello!")),
        maxTokens = 100
      )
      println(claude.createMessage(request).content)
    } finally claude.close()
```

See [Claude API basics](claude/basics.md) for more.

## For Gemini (Google) API

Add the following dependency:

```sbt
"com.softwaremill.sttp.ai" %% "gemini" % "0.5.6"

// For streaming support, add one or more (these modules are shared across OpenAI, Claude, and Gemini):
"com.softwaremill.sttp.ai" %% "fs2" % "0.5.6"    // cats-effect/fs2
"com.softwaremill.sttp.ai" %% "zio" % "0.5.6"    // ZIO
"com.softwaremill.sttp.ai" %% "akka" % "0.5.6"   // Akka Streams (Scala 2.13 only)
"com.softwaremill.sttp.ai" %% "pekko" % "0.5.6"  // Pekko Streams
"com.softwaremill.sttp.ai" %% "ox" % "0.5.6"     // Ox direct-style (Scala 3 only)
```

Then send your first request (reads the `GEMINI_API_KEY` environment variable):

```scala
//> using dep com.softwaremill.sttp.ai::gemini:0.5.6

import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.requests.InteractionRequest

object GeminiHello:
  def main(args: Array[String]): Unit =
    val gemini = GeminiSyncClient.fromEnv
    try {
      val response = gemini.createInteraction(InteractionRequest.simple("gemini-2.5-flash", "Hello!"))
      println(response.outputText)
    } finally gemini.close()
```

See [Gemini API basics](gemini/basics.md) for more.

sttp-ai is available for Scala 2.13 and Scala 3.
