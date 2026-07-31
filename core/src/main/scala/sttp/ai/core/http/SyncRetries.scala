package sttp.ai.core.http

import sttp.client4.{Request, Response, SttpClientException, SyncBackend}
import sttp.model.{HeaderNames, StatusCode}

import scala.annotation.tailrec
import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}

/** Sends requests through a [[sttp.client4.SyncBackend]], retrying transient failures. Used by the sync clients (`OpenAISyncClient`,
  * `ClaudeSyncClient`, `GeminiSyncClient`) to honor `AIClientConfig.maxRetries`.
  *
  * Retried failures:
  *   - connection-level failures ([[sttp.client4.SttpClientException.ConnectException]]) — the request never reached the server, so a retry
  *     is safe even for non-idempotent requests
  *   - responses with status 408 (Request Timeout), 429 (Too Many Requests), or any 5xx
  *
  * Never retried:
  *   - read failures, including read timeouts ([[sttp.client4.SttpClientException.ReadException]]) — the server may have already processed
  *     the request, so a retry could duplicate work and billing
  *   - any other 4xx response
  *
  * Backoff between attempts is exponential: 500ms, 1s, 2s, 4s, ..., capped at 8s. When a retried response carries a `Retry-After` header in
  * delta-seconds form (typically 429/503), that value is used instead, capped at 30s.
  */
object SyncRetries {

  private[http] val InitialBackoff: FiniteDuration = 500.millis
  private[http] val MaxBackoff: FiniteDuration = 8.seconds
  private[http] val MaxRetryAfter: FiniteDuration = 30.seconds

  /** Sends `request`, retrying transient failures up to `maxRetries` times (`maxRetries + 1` attempts in total; `0` disables retries).
    * After retries are exhausted, the last response is returned (or the last connection exception thrown) unchanged.
    *
    * @param sleep
    *   how to wait between attempts (argument in milliseconds); defaults to `Thread.sleep` and is injectable for tests. If interrupted, the
    *   interrupt flag is restored and the [[InterruptedException]] propagates, aborting remaining retries.
    */
  def sendWithRetries[E, A](
      backend: SyncBackend,
      request: Request[Either[E, A]],
      maxRetries: Int,
      sleep: Long => Unit = Thread.sleep(_)
  ): Response[Either[E, A]] = {
    def sleepRestoringInterrupt(millis: Long): Unit =
      try sleep(millis)
      catch {
        case e: InterruptedException =>
          Thread.currentThread().interrupt()
          throw e
      }

    @tailrec
    def attempt(attemptNo: Int): Response[Either[E, A]] = {
      val retriesLeft = maxRetries - attemptNo
      val result =
        try Right(request.send(backend))
        catch { case e: SttpClientException.ConnectException => Left(e) }

      result match {
        case Right(response) if retriesLeft > 0 && shouldRetry(response.code) =>
          sleepRestoringInterrupt(delayMillis(attemptNo, retryAfterMillis(response)))
          attempt(attemptNo + 1)
        case Right(response)            => response
        case Left(_) if retriesLeft > 0 =>
          sleepRestoringInterrupt(delayMillis(attemptNo, None))
          attempt(attemptNo + 1)
        case Left(e) => throw e
      }
    }
    attempt(0)
  }

  private def shouldRetry(code: StatusCode): Boolean =
    code == StatusCode.RequestTimeout || code == StatusCode.TooManyRequests || code.isServerError

  private def retryAfterMillis(response: Response[_]): Option[Long] =
    response
      .header(HeaderNames.RetryAfter)
      .flatMap(_.trim.toLongOption)
      .filter(_ >= 0)
      .map(seconds => math.min(seconds, MaxRetryAfter.toSeconds) * 1000L)

  private def delayMillis(attemptNo: Int, retryAfter: Option[Long]): Long =
    retryAfter.getOrElse((InitialBackoff * (1L << math.min(attemptNo, 30))).min(MaxBackoff).toMillis)
}
