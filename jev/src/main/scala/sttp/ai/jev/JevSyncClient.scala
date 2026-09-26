package sttp.ai.jev

import sttp.ai.core.http.RetryingBackend
import sttp.ai.jev.JevExceptions.JevException
import sttp.client4.{DefaultSyncBackend, Request, SyncBackend}

/** Blocking Jev (TypeSafe AI) client. Throws the [[JevExceptions.JevException]] of a failed call, like the other sync clients. Transient
  * failures are retried `config.maxRetries` times (see [[RetryingBackend]]). Owns `backend`: [[close]] closes it.
  */
class JevSyncClient(config: JevConfig, backend: SyncBackend = DefaultSyncBackend()):
  private val client: JevClient = JevClient(config)
  private val sendBackend: SyncBackend = if config.maxRetries > 0 then RetryingBackend(backend, config.maxRetries) else backend

  private def sendOrThrow[A](request: Request[Either[JevException, A]]): A =
    request.send(sendBackend).body.fold(throw _, identity)

  /** Asks one question about `state`. */
  def ask[A <: Answer](state: Entry, question: Question[A]): SystemOneResponse[A] =
    sendOrThrow(client.ask(state, question))

  /** Asks a tuple of questions about `state`; the answers are a tuple of the matching answer types, position by position, so
    * `val (urgent, team) = ask(state, (noul, choice)).answers` is exactly typed.
    */
  def ask[Qs <: NonEmptyTuple](state: Entry, questions: Qs)(using AllQuestions[Qs]): SystemOneResponse[Answers[Qs]] =
    sendOrThrow(client.ask(state, questions))

  /** Asks a list of questions about `state`, answered in the same order. A mixed list is a `Seq[Question[Answer]]`. */
  def askAll[A <: Answer](state: Entry, questions: Seq[Question[A]]): SystemOneResponse[Seq[A]] =
    sendOrThrow(client.askAll(state, questions))

  def listModels(): Seq[ModelInfo] =
    sendOrThrow(client.listModels())

  def close(): Unit = backend.close()

object JevSyncClient:
  def apply(config: JevConfig): JevSyncClient = new JevSyncClient(config)

  def apply(config: JevConfig, backend: SyncBackend): JevSyncClient = new JevSyncClient(config, backend)

  def fromEnv: JevSyncClient = apply(JevConfig.fromEnv)

  def fromEnv(backend: SyncBackend): JevSyncClient = apply(JevConfig.fromEnv, backend)
