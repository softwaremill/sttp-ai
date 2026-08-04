# Testing agents

The `testkit` module lets you test agent code without hitting a paid LLM API: you script the model's responses, run the real agent loop against them, and assert on what was sent to the "model" and what the agent did.

Add the dependency in test scope:

```sbt
"com.softwaremill.sttp.ai" %% "testkit" % "@VERSION@" % Test
```

## Scripting a conversation

`ScriptedAgent` holds a queue of responses — one per model round-trip — and produces a standard agent builder, so swapping a real agent for a scripted one is a one-line change (`OpenAIAgent.builder(...)` becomes `script.builder`). Tools, system prompt, and all other configuration work exactly as with a real backend, and the real agent loop runs: your tools actually execute.

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::testkit:@VERSION@

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.core.agent.*
import sttp.ai.core.agent.testing.*
import sttp.client4.testing.SyncBackendStub
import sttp.tapir.Schema

class CalculatorAgentSpec extends AnyFlatSpec with Matchers with AgentMatchers {

  case class CalculatorInput(a: Double, b: Double) derives io.circe.Codec.AsObject, Schema

  val calculatorTool = AgentTool.fromFunction(
    "calculator",
    "Adds two numbers"
  ) { (input: CalculatorInput) =>
    s"Result: ${input.a + input.b}"
  }

  "the calculator agent" should "call the tool and produce a final answer" in {
    val script = ScriptedAgent.synchronous(
      ScriptedResponse.toolCall("calculator", """{"a": 1, "b": 2}"""),
      ScriptedResponse.text("The answer is 3")
    )

    val agent = script.builder
      .tools(calculatorTool)
      .build

    val result = agent.run("What is 1 + 2?")(SyncBackendStub)

    // assert on what the agent did
    result should haveCalledTool("calculator")
    result should haveCalledToolWith("calculator", """{"a": 1, "b": 2}""")
    result should haveFinishedWith(FinishReason.NaturalStop)
    result.finalAnswer shouldBe "The answer is 3"

    // assert on what was sent to the "model"
    script should haveReceivedPrompt("What is 1 + 2?")
    script should haveOfferedTool("calculator")
  }
}
```

Response constructors:

- `ScriptedResponse.text(content)` — a final answer; terminates the loop
- `ScriptedResponse.toolCall(name, argsJson)` / `ScriptedResponse.toolCalls(("a", "{}"), ("b", "{}"))` — tool-call requests (ids are generated as `call_1`, `call_2`, ...)
- `ScriptedResponse.textWithToolCalls(content, calls*)` — text and tool calls in one response
- `ScriptedResponse.maxTokens(content)` — a response cut short by the token limit

For anything else (custom stop reasons, explicit call ids), pass a hand-built `AgentResponse`.

If the loop sends more requests than there are scripted responses, the run fails with `ScriptExhaustedException` — a script that's too short signals a loop bug, so there is no silent fallback.

## Asserting without scalatest matchers

Everything the matchers check is available as plain data, so any test framework works:

- `script.requests` — every recorded round-trip: full `ConversationHistory`, the `includeTools` flag, the tools offered (name, description, JSON schema), and the system prompt
- `script.initialPrompt`, `script.userPrompts` — prompts as the model saw them
- `script.offeredTools` — tool name, description, and the exact JSON schema sent
- `script.toolResultsSent` — `(toolName, result)` pairs fed back to the model
- `script.systemPromptSent` — the system prompt built from the agent's configuration
- `result.toolCalls` — executed tool calls with their arguments, outputs, and iterations (from the agent loop itself)

## Effect systems

`ScriptedAgent.synchronous(...)` scripts an `Identity` agent. For other effect types use `ScriptedAgent[F](...)` with the corresponding sttp stub backend, e.g. `ScriptedAgent[IO](...)` with `BackendStub[IO](new CatsMonadAsyncError[IO]())`.
