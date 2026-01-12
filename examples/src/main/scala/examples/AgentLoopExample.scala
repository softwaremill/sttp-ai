package examples

import sttp.ai.core.agent.*
import sttp.ai.claude.ClaudeClient
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.client4.DefaultSyncBackend
import sttp.monad.IdentityMonad
import sttp.shared.Identity

object AgentLoopExample extends App {

  val weatherTool = AgentTool(
    toolName = "get_weather",
    toolDescription = "Get the current weather for a location",
    toolParameters = Map(
      "location" -> ParameterSpec(
        dataType = ParameterType.String,
        description = "The city name"
      ),
      "unit" -> ParameterSpec(
        dataType = ParameterType.String,
        description = "Temperature unit (celsius or fahrenheit)",
        required = false,
        `enum` = Some(Seq("celsius", "fahrenheit"))
      )
    )
  ) { input =>
    val location = input.get("location").map(_.str).getOrElse("Unknown")
    val unit = input.get("unit").map(_.str).getOrElse("celsius")
    s"The weather in $location is 22°${if (unit == "celsius") "C" else "F"}, sunny"
  }

  val calculatorTool = AgentTool(
    toolName = "calculate",
    toolDescription = "Perform a mathematical calculation",
    toolParameters = Map(
      "operation" -> ParameterSpec(
        dataType = ParameterType.String,
        description = "The operation to perform",
        `enum` = Some(Seq("add", "subtract", "multiply", "divide"))
      ),
      "a" -> ParameterSpec(
        dataType = ParameterType.Number,
        description = "First number"
      ),
      "b" -> ParameterSpec(
        dataType = ParameterType.Number,
        description = "Second number"
      )
    )
  ) { input =>
    val operation = input.get("operation").map(_.str).getOrElse("add")
    val a = input.get("a").map(_.num).getOrElse(0.0)
    val b = input.get("b").map(_.num).getOrElse(0.0)

    val result = operation match {
      case "add"      => a + b
      case "subtract" => a - b
      case "multiply" => a * b
      case "divide"   => if (b != 0) a / b else Double.NaN
      case _          => 0.0
    }

    s"$a $operation $b = $result"
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
