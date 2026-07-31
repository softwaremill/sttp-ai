package sttp.ai.core.http

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.client4.testing.ResponseStub
import sttp.model.{Header, HeaderNames, StatusCode}

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer

class SyncRetriesSpec extends AnyFlatSpec with Matchers {

  private val request = basicRequest.get(uri"http://example.org/test")

  /** Returns responses (status, headers) in order; the last entry repeats forever. Counts attempts. */
  private def stubReturning(attempts: AtomicInteger, responses: (StatusCode, Seq[Header])*): SyncBackend =
    DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { _ =>
      val i = attempts.getAndIncrement()
      val (code, headers) = responses(math.min(i, responses.size - 1))
      ResponseStub.adjust("body", code, headers)
    }

  private def noHeaders(codes: StatusCode*): Seq[(StatusCode, Seq[Header])] = codes.map(_ -> Seq.empty[Header])

  "sendWithRetries" should "return a successful response without retrying" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val response = SyncRetries.sendWithRetries(stubReturning(attempts, noHeaders(StatusCode.Ok): _*), request, maxRetries = 3, sleeps += _)
    response.code shouldBe StatusCode.Ok
    attempts.get() shouldBe 1
    sleeps shouldBe empty
  }

  it should "retry a 429 and return the subsequent success" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend = stubReturning(attempts, noHeaders(StatusCode.TooManyRequests, StatusCode.Ok): _*)
    val response = SyncRetries.sendWithRetries(backend, request, maxRetries = 3, sleeps += _)
    response.code shouldBe StatusCode.Ok
    attempts.get() shouldBe 2
    sleeps.toList shouldBe List(500L)
  }

  it should "retry 408 and 5xx statuses" in {
    val attempts = new AtomicInteger(0)
    val backend =
      stubReturning(
        attempts,
        noHeaders(StatusCode.RequestTimeout, StatusCode.InternalServerError, StatusCode.BadGateway, StatusCode.Ok): _*
      )
    SyncRetries.sendWithRetries(backend, request, maxRetries = 3, _ => ()).code shouldBe StatusCode.Ok
    attempts.get() shouldBe 4
  }

  it should "not retry other 4xx responses" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend = stubReturning(attempts, noHeaders(StatusCode.BadRequest, StatusCode.Ok): _*)
    val response = SyncRetries.sendWithRetries(backend, request, maxRetries = 3, sleeps += _)
    response.code shouldBe StatusCode.BadRequest
    attempts.get() shouldBe 1
    sleeps shouldBe empty
  }

  it should "return the last response after exhausting retries" in {
    val attempts = new AtomicInteger(0)
    val backend = stubReturning(attempts, noHeaders(StatusCode.InternalServerError): _*)
    val response = SyncRetries.sendWithRetries(backend, request, maxRetries = 3, _ => ())
    response.code shouldBe StatusCode.InternalServerError
    attempts.get() shouldBe 4
  }

  it should "make exactly one attempt when maxRetries is 0" in {
    val attempts = new AtomicInteger(0)
    val backend = stubReturning(attempts, noHeaders(StatusCode.TooManyRequests, StatusCode.Ok): _*)
    SyncRetries.sendWithRetries(backend, request, maxRetries = 0, _ => ()).code shouldBe StatusCode.TooManyRequests
    attempts.get() shouldBe 1
  }

  it should "back off exponentially, capped at 8 seconds" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend = stubReturning(attempts, noHeaders(StatusCode.InternalServerError): _*)
    SyncRetries.sendWithRetries(backend, request, maxRetries = 6, sleeps += _): Unit
    sleeps.toList shouldBe List(500L, 1000L, 2000L, 4000L, 8000L, 8000L)
  }

  it should "honor a delta-seconds Retry-After header, capped at 30 seconds" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend = stubReturning(
      attempts,
      (StatusCode.TooManyRequests, Seq(Header(HeaderNames.RetryAfter, "2"))),
      (StatusCode.TooManyRequests, Seq(Header(HeaderNames.RetryAfter, "120"))),
      (StatusCode.Ok, Seq.empty[Header])
    )
    SyncRetries.sendWithRetries(backend, request, maxRetries = 3, sleeps += _).code shouldBe StatusCode.Ok
    sleeps.toList shouldBe List(2000L, 30000L)
  }

  it should "fall back to exponential backoff when Retry-After is not delta-seconds" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend = stubReturning(
      attempts,
      (StatusCode.TooManyRequests, Seq(Header(HeaderNames.RetryAfter, "Wed, 21 Oct 2026 07:28:00 GMT"))),
      (StatusCode.Ok, Seq.empty[Header])
    )
    SyncRetries.sendWithRetries(backend, request, maxRetries = 3, sleeps += _).code shouldBe StatusCode.Ok
    sleeps.toList shouldBe List(500L)
  }

  it should "retry connection failures" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { req =>
      if (attempts.getAndIncrement() == 0) throw new SttpClientException.ConnectException(req, new java.net.ConnectException("refused"))
      else ResponseStub.adjust("body", StatusCode.Ok)
    }
    SyncRetries.sendWithRetries(backend, request, maxRetries = 3, sleeps += _).code shouldBe StatusCode.Ok
    attempts.get() shouldBe 2
    sleeps.toList shouldBe List(500L)
  }

  it should "throw the last connection failure after exhausting retries" in {
    val attempts = new AtomicInteger(0)
    val backend = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { req =>
      attempts.getAndIncrement(): Unit
      throw new SttpClientException.ConnectException(req, new java.net.ConnectException("refused"))
    }
    an[SttpClientException.ConnectException] should be thrownBy
      SyncRetries.sendWithRetries(backend, request, maxRetries = 1, _ => ())
    attempts.get() shouldBe 2
  }

  it should "not retry read failures" in {
    val attempts = new AtomicInteger(0)
    val backend = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { req =>
      attempts.getAndIncrement(): Unit
      throw new SttpClientException.TimeoutException(req, new java.util.concurrent.TimeoutException("read timed out"))
    }
    an[SttpClientException.ReadException] should be thrownBy
      SyncRetries.sendWithRetries(backend, request, maxRetries = 3, _ => ())
    attempts.get() shouldBe 1
  }
}
