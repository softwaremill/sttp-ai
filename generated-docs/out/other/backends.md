# Backends and effect systems

## Custom Backend

You can add support for any LLM API by implementing the `AgentBackend` interface:

```scala
trait AgentBackend[F[_]] {
  def sendRequest(
      history: ConversationHistory,
      backend: Backend[F]
  ): F[AgentResponse]
}

case class AgentResponse(
    textContent: String,
    toolCalls: Seq[ToolCall],
    stopReason: Option[String]
)
```

Your implementation needs to:
1. Convert `ConversationHistory` to your API's message format
2. Convert `AgentTool` definitions to your API's tool schema
3. Send request and parse the response into `AgentResponse`

See `OpenAIAgentBackend` and `ClaudeAgentBackend` in source code (`openai/src/main/scala/sttp/ai/openai/agent/` and `claude/src/main/scala/sttp/ai/claude/agent/`) for reference implementations.

## Effect Systems

### Cats Effect

```scala
import cats.effect.{IO, IOApp}
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.ai.openai.agent.OpenAIAgent

object CatsEffectExample extends IOApp.Simple {
  def run: IO[Unit] = {
    val agent = OpenAIAgent.builder[IO](OpenAI.fromEnv, "gpt-4o-mini").maxIterations(5).tools(weatherTool).build
    HttpClientCatsBackend.resource[IO]().use { backend =>
      agent.run("What's the weather in London?")(backend)
        .flatMap(r => IO.println(s"Answer: ${r.finalAnswer}"))
    }
  }
}
```

### ZIO

```scala
import zio.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.ai.openai.agent.OpenAIAgent

object ZIOExample extends ZIOAppDefault {
  def run = {
    val agent = OpenAIAgent.builder[Task](OpenAI.fromEnv, "gpt-4o-mini").maxIterations(5).tools(weatherTool).build
    ZIO.scoped {
      for {
        backend <- HttpClientZioBackend.scoped()
        result <- agent.run("What's the weather in London?")(backend)
        _ <- Console.printLine(s"Answer: ${result.finalAnswer}")
      } yield ()
    }
  }
}
```
