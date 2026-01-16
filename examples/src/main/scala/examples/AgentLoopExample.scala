package examples

import sttp.ai.core.agent.*
import sttp.ai.core.json.SnakePickle
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.client4.DefaultSyncBackend
import sttp.monad.IdentityMonad
import sttp.shared.Identity
import sttp.tapir.Schema

object AgentLoopExample extends App {

  case class WeatherInput(location: String, unit: Option[String]) derives SnakePickle.ReadWriter, Schema

  val weatherTool = AgentTool.fromFunction(
    "get_weather",
    "Get the current weather for a location"
  ) { (input: WeatherInput) =>
    val unit = input.unit.getOrElse("celsius")
    s"The weather in ${input.location} is 22°${if (unit == "celsius") "C" else "F"}, sunny"
  }

  case class CalculatorInput(operation: String, a: Double, b: Double) derives SnakePickle.ReadWriter, Schema

  val calculatorTool = AgentTool.fromFunction(
    "calculate",
    "Perform a mathematical calculation"
  ) { (input: CalculatorInput) =>
    val result = input.operation match {
      case "add"      => input.a + input.b
      case "subtract" => input.a - input.b
      case "multiply" => input.a * input.b
      case "divide"   => if (input.b != 0) input.a / input.b else Double.NaN
      case _          => 0.0
    }

    s"${input.a} ${input.operation} ${input.b} = $result"
  }

  val tools = Seq(weatherTool, calculatorTool)

  val openai = OpenAI.fromEnv
  private val configResult = AgentConfig(
    maxIterations = 8,
    userTools = tools
  )

  println("=== Agent Loop Example ===\n")

  val prompt = "What's the weather in Paris? Also, what is 15 multiplied by 23? Provide a complete answer."

  println(s"User: $prompt\n")

  val backend = DefaultSyncBackend()
  try
    configResult match {
      case Right(config) =>
        val agent = OpenAIAgent[Identity](openai, "gpt-4o-mini", config)(IdentityMonad)

        val result = agent.run(prompt)(backend)

        println("\n=== Agent Result ===")
        println(s"Final Answer: ${result.finalAnswer}")
        println(s"Iterations: ${result.iterations}")
        println(s"Finish Reason: ${result.finishReason}")
        println(s"\nTool Calls (${result.toolCalls.length}):")
        result.toolCalls.foreach { tc =>
          println(s"  [Iteration ${tc.iteration}] ${tc.toolName}")
          println(s"    Input: ${tc.input}")
          println(s"    Output: ${tc.output}")
        }

      case Left(error) =>
        println(s"Configuration error: $error")
    }
  finally backend.close()
}
