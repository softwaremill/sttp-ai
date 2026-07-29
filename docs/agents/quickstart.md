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
      .synchronous(OpenAI.fromEnv, "gpt-4o-mini")
      .maxIterations(5)
      .tools(weatherTool)
      .build

    val result = agent.run("What's the weather in Paris?")(backend)

    println(s"Answer: ${result.finalAnswer}")
    println(s"Iterations: ${result.iterations}")
  } finally backend.close()
}
```

**For Claude:** Use `ClaudeAgent.synchronous(ClaudeConfig.fromEnv, ClaudeModel.ClaudeHaiku4_5.value)` instead.

**For Gemini:** Use `GeminiAgent.synchronous(GeminiConfig.fromEnv, "gemini-2.5-flash")` — see [Gemini tool calling](../gemini/tool-calling.md) for a full example.

**For effect systems:** use `OpenAIAgent.builder[F]` / `ClaudeAgent.builder[F]` / `GeminiAgent.builder[F]` (e.g. `builder[IO]`), then add configuration and `.build`.
