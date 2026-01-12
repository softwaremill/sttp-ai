package sttp.ai.core.agent.integration

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.agent._
import sttp.client4.{Backend, DefaultSyncBackend}
import sttp.shared.Identity

abstract class AgentIntegrationSpecBase extends AnyFlatSpec with Matchers {

  def providerName: String
  def createAgent(maxIterations: Int, tools: Seq[AgentTool]): Agent[Identity]

  protected val calculatorTool: AgentTool = AgentTool(
    toolName = "calculator",
    toolDescription = "Perform basic arithmetic operations",
    toolParameters = Map(
      "operation" -> ParameterSpec(
        dataType = ParameterType.String,
        description = "The operation to perform",
        `enum` = Some(Seq("add", "multiply", "subtract", "divide"))
      ),
      "a" -> ParameterSpec(ParameterType.Number, "First number"),
      "b" -> ParameterSpec(ParameterType.Number, "Second number")
    )
  ) { input =>
    val operation = input.get("operation").map(_.str).getOrElse("add")
    val a = input.get("a").map(_.num).getOrElse(0.0)
    val b = input.get("b").map(_.num).getOrElse(0.0)
    val result = operation match {
      case "add"      => a + b
      case "multiply" => a * b
      case "subtract" => a - b
      case "divide"   => if (b != 0) a / b else 0.0
      case _          => 0.0
    }
    s"$result"
  }

  protected val weatherTool: AgentTool = AgentTool(
    toolName = "get_weather",
    toolDescription = "Get current weather for a city",
    toolParameters = Map(
      "city" -> ParameterSpec(ParameterType.String, "City name")
    )
  ) { input =>
    val city = input.get("city").map(_.str).getOrElse("Unknown")
    s"The weather in $city is sunny, 22°C"
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

  protected def assertToolCalled(result: AgentResult, toolName: String, minTimes: Int = 1): Unit = {
    val callCount = result.toolCalls.count(_.toolName == toolName)
    assert(
      callCount >= minTimes,
      s"Tool '$toolName' should be called at least $minTimes times, but was called $callCount times"
    )
  }

  protected def assertMinIterations(result: AgentResult, min: Int): Unit = {
    assert(
      result.iterations >= min,
      s"Should have at least $min iterations, but had ${result.iterations}"
    )
  }

  def withAgent[T](maxIter: Int, tools: Seq[AgentTool])(test: (Agent[Identity], Backend[Identity]) => T): T = {
  val backend = DefaultSyncBackend()
  try {
    val agent = createAgent(maxIter, tools)
    test(agent, backend)
  } finally backend.close()
  }

  behavior of s"$providerName Agent"

  it should "handle multi-step calculation chain" in withAgent(maxIter = 8, tools = Seq(calculatorTool)) {
    (agent, backend) =>
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

  it should "handle parallel tool usage" in withAgent(maxIter = 6, tools = Seq(calculatorTool, weatherTool)) {
    (agent, backend) =>
      val result = agent.run(
        "Tell me the weather in London, Paris, and Berlin. Also calculate 25 times 4."
      )(backend)

      assertToolCalled(result, "get_weather", minTimes = 3)
      assertToolCalled(result, "calculator")
      assertContainsAll(result.finalAnswer, "london", "paris", "berlin")
      assertContainsAny(result.finalAnswer, "100", "one hundred")
      ()
  }

  it should "handle conditional logic chain" in withAgent(maxIter = 6, tools = Seq(calculatorTool, weatherTool)) {
    (agent, backend) =>
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

  it should "handle list processing" in withAgent(maxIter = 8, tools = Seq(calculatorTool)) {
    (agent, backend) =>
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

  it should "respect max iterations limit" in withAgent(maxIter = 1, tools = Seq(calculatorTool, weatherTool)) {
    (agent, backend) =>
      val result = agent.run(
        "Calculate 5 plus 10, then multiply by 3, then tell me the weather in Paris and London"
      )(backend)

      result.finishReason shouldBe FinishReason.MaxIterations
      result.iterations shouldBe 1
      ()
  }
}
