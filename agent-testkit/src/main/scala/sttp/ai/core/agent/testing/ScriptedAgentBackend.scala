package sttp.ai.core.agent.testing

import sttp.ai.core.agent.{AgentBackend, AgentResponse, AgentTool, ConversationHistory, ResponseSchema}
import sttp.client4.Backend
import sttp.monad.MonadError

/** Thrown when the agent loop sends more requests than there are scripted responses. */
final class ScriptExhaustedException(requestNumber: Int, scriptSize: Int)
    extends Exception(
      s"Scripted responses exhausted: the agent sent request $requestNumber, but only $scriptSize response(s) were scripted"
    )

/** An [[AgentBackend]] that answers each request with the next queued response instead of calling an LLM API, recording every request for
  * assertions. Fails fast with [[ScriptExhaustedException]] when the script runs out — a silent fallback would hide loop bugs.
  *
  * Usually created via [[ScriptedAgent]], which wires tools and system prompt from the agent configuration; instantiate directly when
  * building an [[sttp.ai.core.agent.AgentBackend]] by hand.
  */
final class ScriptedAgentBackend[F[_]](
    script: Seq[AgentResponse],
    val tools: Seq[AgentTool[F, _]],
    val systemPrompt: Option[String],
    responseSchema: Option[ResponseSchema[_]] = None
)(implicit monad: MonadError[F])
    extends AgentBackend[F]
    with RecordedInteractions {

  private var recorded: Vector[RecordedRequest] = Vector.empty

  override def requests: Seq[RecordedRequest] = synchronized(recorded)

  override def sendRequest(
      history: ConversationHistory,
      backend: Backend[F],
      includeTools: Boolean
  ): F[AgentResponse] = monad.suspend {
    val toolsOffered =
      if (includeTools) tools.map(tool => OfferedTool(tool.name, tool.description, tool.rawJsonSchema))
      else Seq.empty
    val index = synchronized {
      recorded = recorded :+ RecordedRequest(history, includeTools, toolsOffered, systemPrompt, responseSchema)
      recorded.length - 1
    }
    if (index < script.length) monad.unit(script(index))
    else monad.error(new ScriptExhaustedException(index + 1, script.length))
  }
}
