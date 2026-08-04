package sttp.ai.core.http

import sttp.capabilities.Effect
import sttp.client4.wrappers.DelegateBackend
import sttp.client4.{
  Backend,
  GenericBackend,
  GenericRequest,
  Response,
  RetryWhen,
  StreamBackend,
  SttpClientException,
  SyncBackend,
  WebSocketBackend,
  WebSocketStreamBackend
}
import sttp.model.{HeaderNames, StatusCode}
import sttp.shared.Identity

import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}

/** A backend wrapper that retries transient failures, in the style of [[sttp.client4.wrappers.FollowRedirectsBackend]]. Generic in the
  * effect: sleeping between attempts is delegated to the caller-supplied `sleep` (the sync overloads default to `Thread.sleep`; effect
  * systems pass their timer, e.g. cats-effect `IO.sleep`, `ZIO.sleep(...)`). The sync clients (`OpenAISyncClient`, `ClaudeSyncClient`,
  * `GeminiSyncClient`) wrap their backend with this to honor `AIClientConfig.maxRetries`.
  *
  * Retried failures:
  *   - connection-level failures ([[sttp.client4.SttpClientException.ConnectException]]) — the request never reached the server, so a retry
  *     is safe even for non-idempotent requests
  *   - responses with status 408 (Request Timeout), 409 (Conflict), 429 (Too Many Requests), or any 5xx — the set the official
  *     OpenAI/Anthropic SDKs retry
  *
  * Never retried:
  *   - read failures, including read timeouts ([[sttp.client4.SttpClientException.ReadException]]) — the server may have already processed
  *     the request, so a retry could duplicate work and billing
  *   - any other 4xx response
  *   - requests whose body cannot be re-sent (streaming/`InputStream` bodies, per [[sttp.client4.RetryWhen.isBodyRetryable]])
  *
  * Backoff between attempts is exponential: 500ms, 1s, 2s, 4s, ..., capped at 8s. When a retried response carries a `Retry-After` header in
  * delta-seconds form (typically 429/503) within [0, 30s], that value is used instead; a `Retry-After` outside that window falls back to
  * the exponential backoff rather than retrying earlier than the server instructed.
  *
  * A retried response's body is closed first when it is [[AutoCloseable]] (e.g. an `InputStream`). Response bodies that are effectful
  * streams cannot be replayed or closed generically — avoid wrapping requests that read *successful* responses as streams if the stream is
  * consumed lazily; error-status responses (the only ones retried) are read eagerly by this library's response handlers, so the library's
  * own requests are always safe.
  */
abstract class RetryingBackend[F[_], P] private (
    delegate: GenericBackend[F, P],
    sleep: FiniteDuration => F[Unit],
    maxRetries: Int
) extends DelegateBackend(delegate) {
  import RetryingBackend._

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] = sendWithRetries(request, attemptNo = 0)

  private def sendWithRetries[T](request: GenericRequest[T, P with Effect[F]], attemptNo: Int): F[Response[T]] = {
    val canRetry = maxRetries - attemptNo > 0 && RetryWhen.isBodyRetryable(request.body)

    val attempted: F[Either[SttpClientException.ConnectException, Response[T]]] =
      monad.handleError(monad.map(delegate.send(request))(r => Right(r): Either[SttpClientException.ConnectException, Response[T]])) {
        case e: SttpClientException.ConnectException if canRetry => monad.unit(Left(e))
      }

    monad.flatMap(attempted) {
      case Right(response) if canRetry && shouldRetry(response.code) =>
        closeIfCloseable(response.body)
        monad.flatMap(sleep(delay(attemptNo, retryAfter(response))))(_ => sendWithRetries(request, attemptNo + 1))
      case Right(response) => monad.unit(response)
      case Left(_)         =>
        monad.flatMap(sleep(delay(attemptNo, None)))(_ => sendWithRetries(request, attemptNo + 1))
    }
  }
}

object RetryingBackend {

  private[http] val InitialBackoff: FiniteDuration = 500.millis
  private[http] val MaxBackoff: FiniteDuration = 8.seconds
  private[http] val MaxRetryAfter: FiniteDuration = 30.seconds

  /** Wraps a sync backend, sleeping between attempts with `Thread.sleep`. If interrupted while sleeping, the interrupt flag is restored and
    * the [[InterruptedException]] propagates, aborting remaining retries. `maxRetries` is the number of retries on top of the initial
    * attempt (`maxRetries + 1` attempts in total; `0` disables retrying).
    */
  def apply(delegate: SyncBackend, maxRetries: Int): SyncBackend =
    apply(delegate, maxRetries, sleepRestoringInterrupt)

  /** Wraps a sync backend with a custom sleep (injectable for tests). */
  def apply(delegate: SyncBackend, maxRetries: Int, sleep: FiniteDuration => Unit): SyncBackend =
    new RetryingBackend[Identity, Any](delegate, sleep, maxRetries) with SyncBackend {}

  /** Wraps any backend; `sleep` supplies the effect's timer, e.g. `IO.sleep` (cats-effect) or `d => ZIO.sleep(...)` (ZIO). */
  def apply[F[_]](delegate: Backend[F], maxRetries: Int, sleep: FiniteDuration => F[Unit]): Backend[F] =
    new RetryingBackend(delegate, sleep, maxRetries) with Backend[F] {}

  def apply[F[_], S](delegate: StreamBackend[F, S], maxRetries: Int, sleep: FiniteDuration => F[Unit]): StreamBackend[F, S] =
    new RetryingBackend(delegate, sleep, maxRetries) with StreamBackend[F, S] {}

  def apply[F[_]](delegate: WebSocketBackend[F], maxRetries: Int, sleep: FiniteDuration => F[Unit]): WebSocketBackend[F] =
    new RetryingBackend(delegate, sleep, maxRetries) with WebSocketBackend[F] {}

  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      maxRetries: Int,
      sleep: FiniteDuration => F[Unit]
  ): WebSocketStreamBackend[F, S] =
    new RetryingBackend(delegate, sleep, maxRetries) with WebSocketStreamBackend[F, S] {}

  private def sleepRestoringInterrupt(d: FiniteDuration): Unit =
    try Thread.sleep(d.toMillis)
    catch {
      case e: InterruptedException =>
        Thread.currentThread().interrupt()
        throw e
    }

  private def shouldRetry(code: StatusCode): Boolean =
    code == StatusCode.RequestTimeout || code == StatusCode.Conflict || code == StatusCode.TooManyRequests || code.isServerError

  private def retryAfter(response: Response[_]): Option[FiniteDuration] =
    response
      .header(HeaderNames.RetryAfter)
      .flatMap(_.trim.toLongOption)
      .filter(seconds => seconds >= 0 && seconds <= MaxRetryAfter.toSeconds)
      .map(_.seconds)

  private def delay(attemptNo: Int, retryAfter: Option[FiniteDuration]): FiniteDuration =
    retryAfter.getOrElse((InitialBackoff * (1L << math.min(attemptNo, 30))).min(MaxBackoff))

  private def closeIfCloseable(body: Any): Unit = body match {
    case c: AutoCloseable =>
      try c.close()
      catch { case _: Exception => () }
    case _ => ()
  }
}
