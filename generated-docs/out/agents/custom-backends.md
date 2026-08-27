# Custom backends and effect systems

## Custom agent backend

The [agent loop](quickstart.md) talks to a model through the `AgentBackend` interface. Implementations are provided for OpenAI, Claude, and Gemini; add support for any other LLM API by implementing it yourself:

```scala
trait AgentBackend[F[_]] {
  def sendRequest(
      history: ConversationHistory,
      backend: Backend[F],
      includeTools: Boolean,
      iterationInfo: IterationInfo
  ): F[AgentResponse]
}

case class AgentResponse(
    textContent: String,
    toolCalls: Seq[ToolCall],
    stopReason: StopReason
)
```

`iterationInfo` tells the backend which loop iteration this request belongs to (1-based) — use it to vary the model per iteration.

Your implementation needs to:
1. Convert `ConversationHistory` to your API's message format
2. Convert `AgentTool` definitions to your API's tool schema
3. Send the request and parse the response into `AgentResponse`

See `OpenAIAgentBackend`, `ClaudeAgentBackend`, and `GeminiAgentBackend` for reference implementations (`openai/src/main/scala/sttp/ai/openai/agent/`, `claude/src/main/scala/sttp/ai/claude/agent/`, and `gemini/src/main/scala/sttp/ai/gemini/agent/`).

## Effect systems

The agent builder is effect-polymorphic: `OpenAIAgent.builder[F]`, `ClaudeAgent.builder[F]`, and `GeminiAgent.builder[F]` all work the same way — pick an sttp backend for your effect type and pass it to `agent.run(...)(backend)`. The examples below use `OpenAIAgent`; substitute the Claude or Gemini builder in the same positions.

### Cats Effect

```scala
//> using dep com.softwaremill.sttp.ai::openai:0.11.0
//> using dep com.softwaremill.sttp.client4::cats:4.0.0-M17

import cats.effect.{IO, IOApp}
import sttp.ai.core.agent.*
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.client4.httpclient.cats.HttpClientCatsBackend
import sttp.client4.impl.cats.implicits.*
import sttp.tapir.Schema

object CatsEffectExample extends IOApp.Simple:
  case class WeatherInput(location: String) derives io.circe.Codec.AsObject, Schema

  val weatherTool = AgentTool.fromFunctionF[IO, WeatherInput](
    "get_weather",
    "Get the current weather for a location"
  ) { (input: WeatherInput) =>
    IO.pure(s"The weather in ${input.location} is 22C, sunny")
  }

  def run: IO[Unit] =
    val agent = OpenAIAgent.builder[IO](OpenAI.fromEnv, "gpt-4o-mini").maxIterations(5).tools(weatherTool).build
    HttpClientCatsBackend.resource[IO]().use { backend =>
      agent.run("What's the weather in London?")(backend)
        .flatMap { r =>
          r.finalAnswer match {
            case Right(answer) => IO.println(s"Answer: $answer")
            case Left(failure) => IO.println(s"Agent did not finish cleanly: $failure")
          }
        }
    }
```

### ZIO

```scala
//> using dep com.softwaremill.sttp.ai::zio:0.11.0

import sttp.ai.core.agent.*
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.client4.impl.zio.RIOMonadAsyncError
import sttp.monad.MonadError
import sttp.tapir.Schema
import zio.*

object ZIOExample extends ZIOAppDefault:
  case class WeatherInput(location: String) derives io.circe.Codec.AsObject, Schema

  val weatherTool = AgentTool.fromFunctionF[Task, WeatherInput](
    "get_weather",
    "Get the current weather for a location"
  ) { (input: WeatherInput) =>
    ZIO.succeed(s"The weather in ${input.location} is 22C, sunny")
  }

  given MonadError[Task] = new RIOMonadAsyncError[Any]

  def run =
    val agent = OpenAIAgent.builder[Task](OpenAI.fromEnv, "gpt-4o-mini").maxIterations(5).tools(weatherTool).build
    ZIO.scoped {
      for {
        backend <- HttpClientZioBackend.scoped()
        result <- agent.run("What's the weather in London?")(backend)
        _ <- result.finalAnswer match {
          case Right(answer) => Console.printLine(s"Answer: $answer")
          case Left(failure) => Console.printLine(s"Agent did not finish cleanly: $failure")
        }
      } yield ()
    }
```
