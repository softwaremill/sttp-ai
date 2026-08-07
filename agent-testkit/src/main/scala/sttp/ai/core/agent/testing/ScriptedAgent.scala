package sttp.ai.core.agent.testing

import sttp.ai.core.agent.{AgentBuilder, AgentResponse}
import sttp.monad.{IdentityMonad, MonadError}
import sttp.shared.Identity

/** Drives an agent through a scripted conversation instead of a real LLM API, and records every request for assertions.
  *
  * {{{
  * val script = ScriptedAgent.synchronous(
  *   ScriptedResponse.toolCall("calculator", """{"a": 1, "b": 2}"""),
  *   ScriptedResponse.text("The answer is 3")
  * )
  * val agent = script.builder.tools(calculatorTool).build
  * val result = agent.run("What is 1 + 2?")(SyncBackendStub)
  * // result.finalAnswer: Either[AgentFailure, String]
  * script.requests // one RecordedRequest per model round-trip
  * }}}
  *
  * A handle represents one scripted conversation — create a fresh one per test. Each `.build` on the returned builder creates a backend
  * that consumes the script from the start; a built agent shares one script cursor across its `run` calls, so build a fresh agent per run.
  * Recordings from all builds accumulate on this handle in creation order. The handle is thread-safe, but `requests` groups recordings by
  * backend-creation order (per `.build`), not globally chronologically across concurrently used builds.
  */
final class ScriptedAgent[F[_]] private (script: Seq[AgentResponse])(implicit monad: MonadError[F]) extends RecordedInteractions {

  private var backends: Vector[ScriptedAgentBackend[F]] = Vector.empty

  /** A standard [[AgentBuilder]] wired to this script — a drop-in replacement for e.g. `OpenAIAgent.builder(...)`. */
  def builder: AgentBuilder[F, ScriptedModel.type, String, String] = AgentBuilder[F, ScriptedModel.type] { config =>
    val backend = new ScriptedAgentBackend[F](script, config.userTools, config.systemPrompt, config.responseSchema)
    synchronized {
      backends = backends :+ backend
    }
    backend
  }

  override def requests: Seq[RecordedRequest] = synchronized {
    backends
  }.flatMap(_.requests)
}

object ScriptedAgent {

  def apply[F[_]](responses: AgentResponse*)(implicit monad: MonadError[F]): ScriptedAgent[F] =
    new ScriptedAgent[F](responses)

  def synchronous(responses: AgentResponse*): ScriptedAgent[Identity] =
    apply[Identity](responses: _*)(IdentityMonad)
}
