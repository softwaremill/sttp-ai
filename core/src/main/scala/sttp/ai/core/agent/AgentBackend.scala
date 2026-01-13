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
  def tools: Seq[AgentTool]

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
    stopReason: Option[String]
)
