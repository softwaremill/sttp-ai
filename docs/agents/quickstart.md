# Agent loop

Framework for building autonomous AI agents that iteratively solve tasks using tool calling. Provides a unified interface for OpenAI, Claude, Gemini, and custom backends.

**Key Features:**

- Unified API for OpenAI, Claude, and Gemini
- Type-safe tool definitions
- Type-safe structured output (optionally)
- Full execution history tracking
- Support for Identity, cats-effect, ZIO, and other effect systems
- Easy [custom backend](custom-backends.md) implementation

## Quick Start

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

import sttp.ai.core.agent.*
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.client4.DefaultSyncBackend
import sttp.tapir.Schema

object BasicExample extends App {
  case class WeatherInput(location: String) derives io.circe.Codec.AsObject, Schema

  val weatherTool = AgentTool.fromFunction(
    "get_weather",
    "Get the current weather for a location"
  ) { (input: WeatherInput) =>
    s"The weather in ${input.location} is 22°C, sunny"
  }

  val backend = DefaultSyncBackend()
  try {
    val agent = OpenAIAgent
      .synchronous(OpenAI.fromEnv, ChatCompletionModel.GPT4oMini)
      .maxIterations(5)
      .tools(weatherTool)
      .build

    val result = agent.run("What's the weather in Paris?")(backend)

    result.finalAnswer match {
      case Right(answer) => println(s"Answer: $answer")
      case Left(failure) => println(s"Agent did not finish cleanly: $failure")
    }
    println(s"Iterations: ${result.iterations}")
  } finally backend.close()
}
```

## Multi-turn conversations

Every `AgentResult` carries the full `ConversationHistory` of the run — the user message, assistant responses, tool calls and their results, and the final answer. To continue talking with full context, pass it back to `run` together with the next user message:

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::openai:@VERSION@

import sttp.ai.core.agent.*
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.client4.DefaultSyncBackend

object ChatExample extends App {
  val backend = DefaultSyncBackend()
  try {
    val agent = OpenAIAgent.synchronous(OpenAI.fromEnv, ChatCompletionModel.GPT4oMini).build

    val first = agent.run("My name is John Doe. What is 2+2?")(backend)
    println(first.finalAnswer)

    // seed the next run with the previous history: the model sees the whole conversation
    val second = agent.run("Multiply that by 10, and remind me of my name.", first.history)(backend)
    println(second.finalAnswer)

    // second.history extends first.history — inspect it, persist it, or feed it into another run
    second.history.entries.foreach(println)
  } finally backend.close()
}
```

A history can also be built (or restored, e.g. from a database) by hand via `ConversationHistory.empty.addUserPrompt(...).addAssistantResponse(...)` and passed as the seed of a fresh run.

**For Claude:** Use `ClaudeAgent.synchronous(ClaudeConfig.fromEnv, ClaudeModel.ClaudeHaiku4_5.value)` instead.

**For Gemini:** Use `GeminiAgent.synchronous(GeminiConfig.fromEnv, "gemini-2.5-flash")` — see [Gemini tool calling](../gemini/tool-calling.md) for a full example.

**For effect systems:** use `OpenAIAgent.builder[F]` / `ClaudeAgent.builder[F]` / `GeminiAgent.builder[F]` (e.g. `builder[IO]`), then add configuration and `.build`.
