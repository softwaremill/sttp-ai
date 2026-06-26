package examples

import sttp.ai.core.agent.*
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.client4.DefaultSyncBackend
import sttp.tapir.Schema

object TypedAgentLoopExample extends App {

  case class TripSummary(weatherSummary: String, calculation: String, conclusion: String) derives io.circe.Codec.AsObject, Schema

  case class WeatherInput(location: String, unit: Option[String]) derives io.circe.Codec.AsObject, Schema

  val weatherTool = AgentTool.fromFunction("get_weather", "Get the current weather for a location") { (input: WeatherInput) =>
    val unit = input.unit.getOrElse("celsius")
    s"The weather in ${input.location} is 22°${if (unit == "celsius") "C" else "F"}, sunny"
  }

  case class CalculatorInput(operation: String, a: Double, b: Double) derives io.circe.Codec.AsObject, Schema

  val calculatorTool = AgentTool.fromFunction("calculate", "Perform a mathematical calculation") { (input: CalculatorInput) =>
    val result = input.operation match {
      case "add"      => input.a + input.b
      case "subtract" => input.a - input.b
      case "multiply" => input.a * input.b
      case "divide"   => if (input.b != 0) input.a / input.b else Double.NaN
      case _          => 0.0
    }
    s"${input.a} ${input.operation} ${input.b} = $result"
  }

  val openai = OpenAI.fromEnv
  val backend = DefaultSyncBackend()
  try {
    val agent = OpenAIAgent
      .synchronous(openai, "gpt-4o-mini")
      .maxIterations(8)
      .tools(weatherTool, calculatorTool)
      .deriveResponseSchema[TripSummary]
      .build
    val prompt = "What's the weather in Paris? Also, what is 15 multiplied by 23? Provide a complete answer."

    try {
      val result = agent.runAs[TripSummary](prompt)(backend)
      result.finalAnswer match {
        case Some(summary) =>
          println(s"weather:     ${summary.weatherSummary}")
          println(s"calculation: ${summary.calculation}")
          println(s"conclusion:  ${summary.conclusion}")
        case None =>
          println(s"Agent did not produce a typed answer (finish reason: ${result.finishReason})")
      }
    } catch {
      case error: AgentDecodeError =>
        println(s"Failed to decode the agent's final answer: ${error.getMessage}")
        println(s"Raw answer was: ${error.rawResult.finalAnswer}")
    }
  } finally backend.close()
}
