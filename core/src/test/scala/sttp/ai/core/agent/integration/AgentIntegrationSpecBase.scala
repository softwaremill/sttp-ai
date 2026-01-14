package sttp.ai.core.agent.integration

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.agent._
import sttp.ai.core.json.SnakePickle
import sttp.client4.{Backend, DefaultSyncBackend}
import sttp.shared.Identity
import sttp.tapir.Schema

abstract class AgentIntegrationSpecBase extends AnyFlatSpec with Matchers {

  def providerName: String
  def apiKeyEnvVar: String
  def createAgent(maxIterations: Int, tools: Seq[AgentTool[_]]): Agent[Identity]

  protected val maybeApiKey: Option[String] = sys.env.get(apiKeyEnvVar)

  case class CalculatorInput(operation: String, a: Double, b: Double)
  implicit val calculatorInputRW: SnakePickle.ReadWriter[CalculatorInput] = SnakePickle.macroRW
  implicit val calculatorInputSchema: Schema[CalculatorInput] = Schema.derived

  protected val calculatorTool: AgentTool[CalculatorInput] = AgentTool.fromFunction(
    "calculator",
    "Perform basic arithmetic operations"
  ) { (input: CalculatorInput) =>
    val result = input.operation match {
      case "add"      => input.a + input.b
      case "multiply" => input.a * input.b
      case "subtract" => input.a - input.b
      case "divide"   => if (input.b != 0) input.a / input.b else 0.0
      case _          => 0.0
    }
    s"$result"
  }

  case class WeatherInput(city: String)
  implicit val weatherInputRW: SnakePickle.ReadWriter[WeatherInput] = SnakePickle.macroRW
  implicit val weatherInputSchema: Schema[WeatherInput] = Schema.derived

  protected val weatherTool: AgentTool[WeatherInput] = AgentTool.fromFunction(
    "get_weather",
    "Get current weather for a city"
  ) { (input: WeatherInput) =>
    s"The weather in ${input.city} is sunny, 22°C"
  }

  protected def assertContainsAny(text: String, options: String*): Unit = {
    val lowerText = text.toLowerCase
    val found = options.exists(opt => lowerText.contains(opt.toLowerCase))
    assert(found, s"Text should contain at least one of: ${options.mkString(", ")}\nActual text: $text")
  }

  protected def assertContainsAll(text: String, required: String*): Unit = {
    val lowerText = text.toLowerCase
    required.foreach { req =>
      assert(lowerText.contains(req.toLowerCase), s"Text should contain '$req'\nActual text: $text")
    }
  }

  protected def assertToolCalled(result: AgentResult[String], toolName: String, minTimes: Int = 1): Unit = {
    val callCount = result.toolCalls.count(_.toolName == toolName)
    assert(
      callCount >= minTimes,
      s"Tool '$toolName' should be called at least $minTimes times, but was called $callCount times"
    )
  }

  protected def assertMinIterations(result: AgentResult[String], min: Int): Unit =
    assert(
      result.iterations >= min,
      s"Should have at least $min iterations, but had ${result.iterations}"
    )

  def withAgent[T](maxIter: Int, tools: Seq[AgentTool[_]])(test: (Agent[Identity], Backend[Identity]) => T): T = {
    if (maybeApiKey.isEmpty) {
      cancel(s"$apiKeyEnvVar not defined - skipping integration test")
    }
    val backend = DefaultSyncBackend()
    try {
      val agent = createAgent(maxIter, tools)
      test(agent, backend)
    } finally backend.close()
  }

  behavior of s"$providerName Agent"

  it should "handle multi-step calculation chain" in withAgent(maxIter = 8, tools = Seq(calculatorTool)) { (agent, backend) =>
    val result = agent.run(
      """I need you to complete this task step by step:
          |1. First, calculate 15 multiplied by 3
          |2. Then, add 20 to that result
          |3. Finally, multiply the result by 2
          |Please use the calculator tool for each step and show your work. Return number""".stripMargin
    )(backend)

    assertMinIterations(result, 3)
    assertToolCalled(result, "calculator", minTimes = 3)
    assertContainsAny(result.finalAnswer, "130", "one hundred thirty", "hundred and thirty")
    result.finishReason should (be(FinishReason.ToolFinish) or be(FinishReason.NaturalStop))
    ()
  }

  it should "handle parallel tool usage" in withAgent(maxIter = 6, tools = Seq(calculatorTool, weatherTool)) { (agent, backend) =>
    val result = agent.run(
      "Tell me the weather in London, Paris, and Berlin. Also calculate 25 times 4."
    )(backend)

    assertToolCalled(result, "get_weather", minTimes = 3)
    assertToolCalled(result, "calculator")
    assertContainsAll(result.finalAnswer, "london", "paris", "berlin")
    assertContainsAny(result.finalAnswer, "100", "one hundred")
    ()
  }

  it should "handle conditional logic chain" in withAgent(maxIter = 6, tools = Seq(calculatorTool, weatherTool)) { (agent, backend) =>
    val result = agent.run(
      """Please do the following:
          |1. Calculate 10 plus 15
          |2. If the result is greater than 20, multiply it by 2, otherwise add 5
          |3. Then tell me the weather in Tokyo""".stripMargin
    )(backend)

    assertMinIterations(result, 2)
    assertToolCalled(result, "calculator", minTimes = 2)
    assertToolCalled(result, "get_weather")
    assertContainsAny(result.finalAnswer, "50", "fifty")
    assertContainsAny(result.finalAnswer, "tokyo")
    ()
  }

  it should "handle list processing" in withAgent(maxIter = 8, tools = Seq(calculatorTool)) { (agent, backend) =>
    val result = agent.run(
      """I have a list of numbers: 5, 8, 12.
          |For each number, multiply it by 3 using the calculator.
          |Then sum all the results.""".stripMargin
    )(backend)

    assertMinIterations(result, 3)
    assertToolCalled(result, "calculator", minTimes = 3)
    assertContainsAny(result.finalAnswer, "75", "seventy")
    ()
  }

  it should "respect max iterations limit" in withAgent(maxIter = 1, tools = Seq(calculatorTool, weatherTool)) { (agent, backend) =>
    val result = agent.run(
      "Calculate 5 plus 10, then multiply by 3, then tell me the weather in Paris and London"
    )(backend)

    result.finishReason shouldBe FinishReason.MaxIterations
    result.iterations shouldBe 1
    ()
  }
}
