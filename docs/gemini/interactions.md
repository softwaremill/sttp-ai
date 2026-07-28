# Interactions

The Interactions API models a conversation as a single resource — an *interaction* — rather than a list of messages you resend on every call. Each interaction has a lifecycle (create, fetch, cancel, delete) and can either be persisted server-side or replayed statelessly from your own history.

## The Interaction Lifecycle

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::gemini:@VERSION@

import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.requests.InteractionRequest

object LifecycleExample:
  def main(args: Array[String]): Unit =
    val gemini = GeminiSyncClient.fromEnv
    try {
      // Create
      val created = gemini.createInteraction(
        InteractionRequest.simple("gemini-2.5-flash", "Reply with exactly one word: ping")
      )
      println(s"status: ${created.status}")

      // Get (only meaningful when the interaction was stored server-side)
      val fetched = gemini.getInteraction(created.id)
      println(fetched.outputText)

      // Cancel a still-running interaction (e.g. one created with background = Some(true))
      // gemini.cancelInteraction(created.id)

      // Delete
      gemini.deleteInteraction(created.id)
    } finally gemini.close()
```

`createInteraction`/`getInteraction`/`cancelInteraction` return an `InteractionResponse`; `deleteInteraction` returns `Unit`. All four throw a `GeminiException` subclass on failure on the sync client, or hand back an `Either[GeminiException, A]` on the async `GeminiClient` (see [basics.md](basics.md)).

## `store`: server-side persistence

The API's own default is `store = true` — every interaction you create is persisted server-side unless you opt out. Pass `store = Some(false)` to skip persistence (nothing to fetch or delete afterwards):

```scala
val request = InteractionRequest
  .simple("gemini-2.5-flash", "Reply with exactly one word: stored")
  .copy(store = Some(true)) // the default; shown here for clarity

val ephemeral = InteractionRequest
  .simple("gemini-2.5-flash", "Reply with exactly one word: ephemeral")
  .copy(store = Some(false))
```

## Multi-turn conversations

There are two ways to continue a conversation, matching the two `store` modes:

- **Server-side state** — set `store = Some(true)` (or leave it unset) on the first call, then pass the returned interaction's `id` as `previousInteractionId` on the next request. Gemini reconstructs the conversation from what it already persisted.
- **Stateless replay** — set `store = Some(false)` and pass the *entire* conversation so far as `InteractionInput.StepsInput(steps)`. This is what `GeminiAgent`'s backend always does internally (see [tool-calling.md](tool-calling.md)), since an agent loop shouldn't depend on server-side state surviving between iterations.

```scala mdoc:compile-only
//> using dep com.softwaremill.sttp.ai::gemini:@VERSION@

import sttp.ai.gemini.GeminiSyncClient
import sttp.ai.gemini.models.{Content, InteractionInput, Step}
import sttp.ai.gemini.requests.InteractionRequest

object MultiTurnExample:
  def main(args: Array[String]): Unit =
    val gemini = GeminiSyncClient.fromEnv
    try {
      // Server-side: continue via previousInteractionId
      val first = gemini.createInteraction(InteractionRequest.simple("gemini-2.5-flash", "My name is Ada."))
      val second = gemini.createInteraction(
        InteractionRequest
          .simple("gemini-2.5-flash", "What is my name?")
          .copy(previousInteractionId = Some(first.id))
      )
      println(second.outputText)

      // Stateless: replay the whole conversation as steps
      val steps = List(
        Step.userText("My name is Ada."),
        Step.ModelOutput(List(Content.Text("Nice to meet you, Ada!"))),
        Step.userText("What is my name?")
      )
      val replayed = gemini.createInteraction(
        InteractionRequest(
          model = "gemini-2.5-flash",
          input = InteractionInput.StepsInput(steps),
          store = Some(false)
        )
      )
      println(replayed.outputText)
    } finally gemini.close()
```

## `Step` and `Content`: the conversation model

`Step` is the unit of both request `input` (when using `InteractionInput.StepsInput`) and response `steps`:

```scala
sealed trait Step
object Step {
  case class UserInput(content: List[Content]) extends Step
  case class ModelOutput(content: List[Content]) extends Step
  case class FunctionCall(id: String, name: String, arguments: io.circe.Json) extends Step
  case class FunctionResult(callId: String, name: String, result: io.circe.Json) extends Step

  def userText(text: String): Step = UserInput(List(Content.Text(text)))
}
```

`Content` is the payload carried by `UserInput`/`ModelOutput` steps — plain text or a media reference (inline base64 `data` or a remote `uri`, with an optional `mimeType`):

```scala
sealed trait Content
object Content {
  case class Text(text: String) extends Content
  case class Image(data: Option[String] = None, uri: Option[String] = None, mimeType: Option[String] = None) extends Content
  case class Audio(data: Option[String] = None, uri: Option[String] = None, mimeType: Option[String] = None) extends Content
  case class Video(data: Option[String] = None, uri: Option[String] = None, mimeType: Option[String] = None) extends Content
  case class Document(data: Option[String] = None, uri: Option[String] = None, mimeType: Option[String] = None) extends Content
}
```

`InteractionResponse` exposes two convenience accessors built on top of `steps`:

- `outputText: String` — all text content of the *last* `ModelOutput` step, concatenated
- `functionCalls: List[Step.FunctionCall]` — every function call the model requested, in order

See [tool-calling.md](tool-calling.md) for how to answer those function calls with `Step.FunctionResult`.
