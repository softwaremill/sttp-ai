//> using dep com.softwaremill.sttp.ai::openai:0.5.2
//> using dep ch.qos.logback:logback-classic:1.5.38
// NOTE: the pinned release above does NOT yet contain the "no tools on the final iteration" behaviour, so running this
// file directly with scala-cli would log `tools offered: true, true, true`. Run it against the local build to observe the
// fix (see the run instructions below); the dependency line is bumped to the release that includes it at publish time.

package examples

import sttp.ai.core.agent.*
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.capabilities.Effect
import sttp.client4.wrappers.DelegateBackend
import sttp.client4.{Backend, DefaultSyncBackend, GenericRequest, Response, StringBody, SyncBackend}
import sttp.shared.Identity
import sttp.tapir.Schema

/** Production-like check for "no tools on the final agent iteration".
  *
  * The agent is given a task that needs several sequential tool calls, but only `maxIterations = 3` in which to solve it. Iterations 0 and
  * 1 are offered the calculator tool; on the last iteration (index 2) the loop sends the request WITHOUT tools, forcing a final text
  * answer.
  *
  * We wrap the HTTP backend in a small logging decorator that inspects each outgoing request body and prints whether it declared `tools`.
  * The expected output is `true, true, false` — and, crucially, the run finishes with a real answer and `FinishReason.MaxIterations`
  * instead of throwing an API error. A successful finish is the evidence that the provider accepts a tools-omitted request whose message
  * history still contains the earlier tool_use / tool_result entries.
  *
  * Run against the local build (not the published artifact) with:
  * {{{
  * OPENAI_API_KEY=sk-... sbt "examples/runMain examples.FinalIterationNoToolsExample"
  * }}}
  */
object FinalIterationNoToolsExample extends App {

  /** Logs, per real LLM call, whether the serialized request body declared any tools. */
  class ToolVisibilityLoggingBackend(delegate: SyncBackend) extends DelegateBackend[Identity, Any](delegate) with Backend[Identity] {
    private var callNumber = 0

    override def send[T](request: GenericRequest[T, Any & Effect[Identity]]): Response[T] = {
      callNumber += 1
      val toolsOffered = request.body match {
        case StringBody(body, _, _) => body.contains("\"tools\"")
        case _                      => false
      }
      println(s"[LLM call #$callNumber] tools offered in request body: $toolsOffered")
      delegate.send(request)
    }
  }

  case class CalculatorInput(operation: String, a: Double, b: Double) derives io.circe.Codec.AsObject, Schema

  val calculatorTool = AgentTool.fromFunction(
    "calculate",
    "Perform a single arithmetic operation (one of: add, subtract, multiply, divide)"
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

  val prompt =
    """Use the calculate tool for EVERY arithmetic step (never compute in your head):
      |1. add 17 and 25
      |2. multiply that sum by 3
      |3. subtract 6 from that result
      |Then state the final number in one sentence.""".stripMargin

  val maxIterations = 3

  println("=== Final-iteration no-tools example ===\n")
  println(s"maxIterations = $maxIterations")
  println(s"User: $prompt\n")

  val openai = OpenAI.fromEnv
  val backend = new ToolVisibilityLoggingBackend(DefaultSyncBackend())
  try {
    val agent = OpenAIAgent
      .synchronous(openai, "gpt-4o-mini")
      .maxIterations(maxIterations)
      .tools(calculatorTool)
      .build

    val result = agent.run(prompt)(backend)

    println("\n=== Result ===")
    println(s"Finish reason: ${result.finishReason}")
    println(s"Iterations:    ${result.iterations}")
    println(s"Tool calls (${result.toolCalls.length}):")
    result.toolCalls.foreach { tc =>
      println(s"  [iteration ${tc.iteration}] ${tc.toolName}(${tc.input}) -> ${tc.output}")
    }
    println(s"Final answer:  ${result.finalAnswer}")

    val lastIterationHadTools = result.toolCalls.exists(_.iteration >= maxIterations)
    println("\n=== Interpretation ===")
    println(s"- No tool call executed on the final iteration ($maxIterations): ${!lastIterationHadTools}")
    println(s"- Run completed without an API error and reached ${result.finishReason}")
    println("- Expect the LLM-call log above to read: tools offered = true, true, false")
  } finally backend.close()
}
