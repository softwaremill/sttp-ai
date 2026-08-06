# Agent configuration

## Agent Configuration

Configure the agent with the fluent builder. Use `OpenAIAgent.builder[F]` / `ClaudeAgent.builder[F]` for an effect system (e.g. `builder[IO]`), or `synchronous(...)` for the blocking `Identity` clients:

```scala
val agent = OpenAIAgent
  .synchronous(OpenAI.fromEnv, "gpt-4o-mini")
  .maxIterations(10)                               // Max reasoning steps
  .systemPrompt("Custom prompt")                   // Optional instructions
  .tools(tool1, tool2)                             // Your tools
  .deriveResponseSchema[T]                          // Optional typed result (see runAs[T] below)
  .build
```

`maxIterations` bounds the total number of model calls. The **last** allowed iteration is sent without tools, forcing the
model to produce a final text answer instead of a tool call whose result would be discarded (that iteration finishes with
`FinishReason.MaxIterations`). Tools are therefore only available for the first `maxIterations - 1` iterations — so with
`maxIterations = 1` the agent never gets to use its tools. Set `maxIterations` at least one higher than the number of
tool-using steps you expect the task to need.

The OpenAI factories additionally accept a `strictTools` flag (default `true`): when `true`, tool schemas are
normalized for OpenAI's strict function calling (`additionalProperties: false`, all properties required, optional
properties nullable); when `false`, tools are registered as non-strict with their original schemas.

## Model capabilities

Model constants are tagged with capability marker traits: `Vision`, `ToolCalling`, `StructuredOutput`, `Reasoning`
(in `sttp.ai.core.model.Capability`; models supporting everything mix in the `Capability.All` shorthand, which
extends all four). Agent builder methods that need a capability require it at compile time:
`.tools`/`.addTool` need `ToolCalling`, `.responseSchema`/`.deriveResponseSchema` need `StructuredOutput`.

```scala mdoc:compile-only
import sttp.ai.core.agent.AgentTool
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.shared.Identity

def myTool: AgentTool[Identity, ?] = ???

val agent = OpenAIAgent
  .synchronous(OpenAI.fromEnv, ChatCompletionModel.GPT4o) // GPT4o mixes in ToolCalling
  .tools(myTool)
  .build

// OpenAIAgent.synchronous(OpenAI.fromEnv, ChatCompletionModel.O1Mini).tools(myTool) // does not compile: o1-mini has no ToolCalling
```

String model names keep working and skip capability checking — they wrap into the provider's custom model class,
which claims all capabilities (you assert your model supports what you use it for):

```scala mdoc:compile-only
import sttp.ai.core.agent.AgentTool
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.shared.Identity

def myTool: AgentTool[Identity, ?] = ???

val ollamaAgent = OpenAIAgent.synchronous(OpenAI.fromEnv, "llama3-70b").tools(myTool).build
```

### Custom models and wrong tags

The provider model hierarchies are sealed, so you cannot define your own constant with hand-picked capability tags —
a model outside the predefined constants always goes through the provider's custom model class and claims all
capabilities. If a *predefined* constant's tags are missing or wrong (capability data is curated and can lag the
providers), `Supports.assume` opts out of checking for exactly that model/capability pair while keeping every other
check intact — unlike falling back to a raw model-name string, which disables all checking:

```scala mdoc:compile-only
import sttp.ai.core.model.{Capability, Supports}
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel

// you have verified the model really supports this, even though its constant isn't tagged with it
given Supports[ChatCompletionModel.GPT4o20240513.type, Capability.StructuredOutput] = Supports.assume
```

Note that capability checks work on the model's singleton type: ascribing a constant to the base type
(`val m: ChatCompletionModel = ChatCompletionModel.GPT4o`) widens away the tags, and `.tools(...)` on it will not
compile. Pass constants directly, or keep the precise type (`ChatCompletionModel.GPT4o.type`).

## Per-iteration model selection

Agents can use a different model per loop iteration — e.g. a cheap model while the agent calls tools, and a stronger
model for the forced-final iteration (where tools are withheld and the model must answer):

```scala mdoc:compile-only
import sttp.ai.core.agent.{AgentTool, IterationInfo}
import sttp.ai.openai.OpenAI
import sttp.ai.openai.agent.OpenAIAgent
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.shared.Identity

def myTool: AgentTool[Identity, ?] = ???

val mixedAgent = OpenAIAgent
  .synchronous(OpenAI.fromEnv, (info: IterationInfo) => if info.isLastIteration then ChatCompletionModel.GPT5 else ChatCompletionModel.GPT4oMini)
  .tools(myTool)
  .build
```

The inferred model type is the least upper bound of every model the function can return, so capability checks require
the capabilities **all** of them share. If inference produces an unwieldy type, ascribe the function explicitly:

```scala mdoc:compile-only
import sttp.ai.core.agent.IterationInfo
import sttp.ai.core.model.Capability
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel

val pick: IterationInfo => ChatCompletionModel & Capability.ToolCalling =
  info => if info.isLastIteration then ChatCompletionModel.GPT5 else ChatCompletionModel.GPT4oMini
```

Note: `isLastIteration` flags only the *forced* last iteration (`maxIterations` reached). The loop cannot know in
advance on which iteration the model will answer naturally.

## Hooks

The loop can invoke optional effectful hooks around each tool call. Both run inside the agent loop, so an error in either hook interrupts the run.

### beforeToolCall

If defined, the loop invokes `beforeToolCall` once before each tool call is executed, passing the pending `ToolCall`.

```scala
val agent = OpenAIAgent.builder[IO](OpenAI.fromEnv, "gpt-4o-mini")
  .maxIterations(10)
  .tools(tool1, tool2)
  .hookBeforeToolCall(call => IO.println(s"calling ${call.toolName}(${call.input})"))
  .build
```

`ToolCall` carries `id`, `toolName`, and `input`.

### afterToolCall

If defined, the loop invokes `afterToolCall` once after each tool call, passing the `ToolCallRecord`.

```scala
val agent = OpenAIAgent.builder[IO](OpenAI.fromEnv, "gpt-4o-mini")
  .maxIterations(10)
  .tools(tool1, tool2)
  .hookAfterToolCall(call => IO.println(s"[step ${call.iteration}] ${call.toolName} -> ${call.output}"))
  .build
```

`ToolCallRecord` carries `id`, `toolName`, `input`, `output` (the successful output, or the error message fed back to the LLM on failure), and `iteration`.

## Exception Handling

The `ExceptionHandler` controls how tool execution errors and argument parsing failures are handled. You can choose between built-in handlers or create custom ones.

**Built-in Handlers:**

| Handler | Tool Execution Errors | Parse Errors | Use Case |
|---------|----------------------|--------------|----------|
| `ExceptionHandler.default` | IO/Interrupt errors propagate; others sent to LLM | Sent to LLM with descriptive message | **Recommended for most cases** |
| `ExceptionHandler.sendAllToLLM` | All errors sent to LLM | All errors sent to LLM | Let LLM recover from all errors |
| `ExceptionHandler.propagateAll` | All errors propagate | All errors propagate | Strict mode, fail fast |

**Default Handler (recommended):**

```scala
val agent = OpenAIAgent
  .synchronous(OpenAI.fromEnv, "gpt-4o-mini")
  .maxIterations(5)
  .tools(myTool)
  .exceptionHandler(ExceptionHandler.default) // This is the default, can be omitted
  .build
```

The default handler:
- **Propagates** `IOException` and `InterruptedException` (system-level errors that typically can't be recovered)
- **Sends to LLM** all other exceptions with descriptive error messages, allowing the agent to retry or adjust

**Send All to LLM:**

```scala
val agent = OpenAIAgent
  .synchronous(OpenAI.fromEnv, "gpt-4o-mini")
  .maxIterations(5)
  .tools(myTool)
  .exceptionHandler(ExceptionHandler.sendAllToLLM)
  .build
```

All errors are converted to messages and sent to the LLM, giving it maximum opportunity to recover.

**Propagate All (Strict Mode):**

```scala
val agent = OpenAIAgent
  .synchronous(OpenAI.fromEnv, "gpt-4o-mini")
  .maxIterations(5)
  .tools(myTool)
  .exceptionHandler(ExceptionHandler.propagateAll)
  .build
```

All errors immediately terminate the agent loop by propagating the exception. Use this for strict error handling where any failure should stop execution.

**Custom Handler:**

```scala
val customHandler = new ExceptionHandler {
  def handleToolException(toolName: String, exception: Exception): Either[String, Exception] =
    exception match {
      case e: MyRecoverableException =>
        Left(s"Recoverable error in $toolName: ${e.getMessage}")
      case other =>
        Right(other)  // Propagate
    }

  def handleParseError(
      toolName: String,
      rawArguments: String,
      parseException: Exception
  ): Either[String, Exception] =
    Left(s"Invalid arguments for $toolName - please check the schema")
}

val agent = OpenAIAgent
  .synchronous(OpenAI.fromEnv, "gpt-4o-mini")
  .maxIterations(5)
  .tools(myTool)
  .exceptionHandler(customHandler)
  .build
```

Return `Left(message)` to send the error to the LLM and continue the loop, or `Right(exception)` to propagate and terminate.
