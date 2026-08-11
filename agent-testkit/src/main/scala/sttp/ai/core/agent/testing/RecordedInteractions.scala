package sttp.ai.core.agent.testing

import io.circe.Json
import sttp.ai.core.agent.{ConversationEntry, ConversationHistory, IterationInfo, ResponseSchema}

/** A tool as it was offered to the (scripted) model: name, description, and the input JSON schema the model would see. */
final case class OfferedTool(name: String, description: String, schema: Json)

/** One recorded model round-trip.
  *
  * @param history
  *   the full conversation history as the model would see it
  * @param includeTools
  *   whether tools were offered on this call (the agent loop sets this to false on the last allowed iteration)
  * @param toolsOffered
  *   the tools offered on this call; empty when includeTools is false
  * @param systemPrompt
  *   the system prompt configured for the agent, as built from its [[sttp.ai.core.agent.AgentConfig]]
  * @param responseSchema
  *   the structured-output schema configured for the agent (e.g. via `deriveResponseSchema[T]`), if any
  * @param iterationInfo
  *   the position of this request within the agent loop (1-based iteration, and the configured maximum)
  */
final case class RecordedRequest(
    history: ConversationHistory,
    includeTools: Boolean,
    toolsOffered: Seq[OfferedTool],
    systemPrompt: Option[String],
    responseSchema: Option[ResponseSchema[_]],
    iterationInfo: IterationInfo
)

/** Framework-agnostic query API over the requests recorded by a scripted backend. Everything the scalatest matchers assert on is reachable
  * through these accessors, so any test framework can be used.
  */
trait RecordedInteractions {

  /** All recorded requests, one per model round-trip, in order. */
  def requests: Seq[RecordedRequest]

  /** The first user prompt of the first request — the prompt `Agent.run` was called with. */
  final def initialPrompt: Option[String] =
    requests.headOption.flatMap(_.history.entries.collectFirst { case ConversationEntry.UserPrompt(content) => content })

  /** All user prompts in the final history, in order, including iteration markers as sent (rendered as `[Iteration i of n]`). */
  final def userPrompts: Seq[String] =
    requests.lastOption.toSeq.flatMap(_.history.entries.collect {
      case ConversationEntry.UserPrompt(content)                     => content
      case ConversationEntry.IterationMarker(current, maxIterations) => s"[Iteration $current of $maxIterations]"
    })

  /** The tools offered on any request, deduplicated by name. Note that the agent loop withholds tools on the last allowed iteration, so
    * with `maxIterations(1)` no tools are ever offered and this is empty.
    */
  final def offeredTools: Seq[OfferedTool] = {
    val seen = scala.collection.mutable.Set.empty[String]
    requests.flatMap(_.toolsOffered).filter(t => seen.add(t.name))
  }

  /** All (toolName, result) pairs fed back to the model, from the final history, in order. */
  final def toolResultsSent: Seq[(String, String)] =
    requests.lastOption.toSeq.flatMap(_.history.entries.collect { case ConversationEntry.ToolResult(_, toolName, result) =>
      (toolName, result)
    })

  /** The system prompt sent on the first request. */
  final def systemPromptSent: Option[String] = requests.headOption.flatMap(_.systemPrompt)

  /** The structured-output schema configured for the agent, from the first request. */
  final def responseSchemaSent: Option[ResponseSchema[_]] = requests.headOption.flatMap(_.responseSchema)
}
