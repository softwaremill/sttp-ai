package sttp.ai.core.agent

import sttp.client4.Backend

/** Abstracts the communication between an agent and a specific LLM API (OpenAI, Claude, etc.).
  *
  * Converts generic [[ConversationHistory]] into API-specific request formats, sends HTTP requests via the provided sttp [[Backend]], and
  * parses responses back into generic [[AgentResponse]] objects. This decouples [[Agent]] from specific API implementations.
  *
  * The HTTP backend is passed as a parameter (not stored) to give users control over configuration and lifecycle. The conversation history
  * is provided because different APIs format messages differently (e.g., OpenAI uses role-based messages, Claude uses separate system
  * parameter).
  *
  * @tparam F
  *   The effect type (Identity, Future, cats.effect.IO, zio.Task, etc.)
  */
trait AgentBackend[F[_]] {

  /** The tools available to the agent */
  def tools: Seq[AgentTool[_]]

  /** Optional system prompt to guide the agent's behavior */
  def systemPrompt: Option[String]

  /** Sends a request to the LLM API with the current conversation history.
    *
    * @param history
    *   The complete conversation history including prompts, responses, tool calls, and results
    * @param backend
    *   The HTTP backend used to send requests (supports various effect systems via sttp)
    * @return
    *   The LLM's response wrapped in the effect type F, containing text content, tool calls, and stop reason
    */
  def sendRequest(
      history: ConversationHistory,
      backend: Backend[F]
  ): F[AgentResponse]
}

case class AgentResponse(
    textContent: String,
    toolCalls: Seq[ToolCall],
    stopReason: StopReason
)

/** Represents why the LLM stopped generating.
  *
  * This abstracts over different API-specific stop reasons (OpenAI: "stop", "tool_calls", "length"; Claude: "end_turn", "tool_use",
  * "max_tokens").
  */
sealed trait StopReason

object StopReason {

  /** The model finished naturally (OpenAI: "stop", Claude: "end_turn") */
  case object EndTurn extends StopReason

  /** The model wants to call one or more tools (OpenAI: "tool_calls", Claude: "tool_use") */
  case object ToolUse extends StopReason

  /** Maximum token limit was reached (OpenAI: "length", Claude: "max_tokens") */
  case object MaxTokens extends StopReason

  /** A stop sequence was encountered (Claude: "stop_sequence") */
  case object StopSequence extends StopReason

  /** Content was filtered (OpenAI: "content_filter") */
  case object ContentFilter extends StopReason

  /** Unknown or unrecognized stop reason */
  case class Other(reason: String) extends StopReason
}
