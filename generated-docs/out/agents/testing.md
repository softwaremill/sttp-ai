# Testing agents

The `agent-testkit` module lets you test agent code without hitting a paid LLM API: you script the model's responses, run the real agent loop against them, and assert on what was sent to the "model" and what the agent did.

This serves two use-cases:

- **Testing agent wiring directly** — verifying that your tools execute correctly, that prompts and schemas reach the model as intended, and that the loop behaves (as in the example below).
- **Integration-testing code that takes an agent as a parameter** — a scripted agent is a stub `Agent`: scripted to invoke given tools and to produce a given final answer, it can stand in for the real thing anywhere a larger process is parameterized by an agent, keeping that process's tests deterministic and offline.

Add the dependency in test scope:

```sbt
"com.softwaremill.sttp.ai" %% "agent-testkit" % "0.9.0" % Test
```

The scalatest dependency is `Provided`: to use the matchers, bring your own scalatest (any 3.2.x) — which a scalatest test suite already has. The plain query API (see below) needs no test-framework dependency at all.

## Scripting a conversation

`ScriptedAgent` holds a queue of responses — one per model round-trip — and produces a standard agent builder, so swapping a real agent for a scripted one is a one-line change (`OpenAIAgent.builder(...)` becomes `script.builder`). Tools, system prompt, and all other configuration work exactly as with a real backend, and the real agent loop runs: your tools actually execute.

```scala
//> using dep com.softwaremill.sttp.ai::agent-testkit:0.9.0

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
    result.finalAnswer shouldBe Right("The answer is 3")

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

A `ScriptedAgent` handle represents one scripted conversation: create a fresh one per test. A built agent shares one script cursor across its `run` calls (a second `run` continues where the first stopped, usually into exhaustion), while calling `.build` again starts a fresh backend that replays the script from the start and appends its recordings to `script.requests` — so build one agent and run it once per script.

## Asserting without scalatest matchers

Everything the matchers check is available as plain data, so any test framework works:

- `script.requests` — every recorded round-trip: full `ConversationHistory`, the `includeTools` flag, the tools offered (name, description, JSON schema), and the system prompt
- `script.initialPrompt`, `script.userPrompts` — prompts as the model saw them
- `script.offeredTools` — tool name, description, and the exact JSON schema sent
- `script.toolResultsSent` — `(toolName, result)` pairs fed back to the model
- `script.systemPromptSent` — the system prompt built from the agent's configuration
- `script.responseSchemaSent` — the structured-output schema configured for the agent (e.g. via `deriveResponseSchema[T]`), if any
- `result.toolCalls` — executed tool calls with their arguments, outputs, and iterations (from the agent loop itself)

## Effect systems

`ScriptedAgent.synchronous(...)` scripts an `Identity` agent. For other effect types use `ScriptedAgent[F](...)` with the corresponding sttp stub backend — an implicit `MonadError[F]` must be in scope, e.g. `ScriptedAgent[IO](responses*)(using new CatsMonadAsyncError[IO]())` with `BackendStub[IO](new CatsMonadAsyncError[IO]())`.
