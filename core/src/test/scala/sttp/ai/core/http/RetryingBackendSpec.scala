package sttp.ai.core.http

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.client4.testing.{BackendStub, ResponseStub}
import sttp.model.{Header, HeaderNames, StatusCode}

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class RetryingBackendSpec extends AnyFlatSpec with Matchers {

  private val request = basicRequest.get(uri"http://example.org/test")

  /** Returns responses (status, headers) in order; the last entry repeats forever. Counts attempts. */
  private def stubReturning(attempts: AtomicInteger, responses: (StatusCode, Seq[Header])*): SyncBackend =
    DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { _ =>
      val i = attempts.getAndIncrement()
      val (code, headers) = responses(math.min(i, responses.size - 1))
      ResponseStub.adjust("body", code, headers.toList)
    }

  private def noHeaders(codes: StatusCode*): Seq[(StatusCode, Seq[Header])] = codes.map(_ -> Seq.empty[Header])

  private def recording(sleeps: ListBuffer[Long]): FiniteDuration => Unit = d => { sleeps += d.toMillis; () }

  "RetryingBackend" should "return a successful response without retrying" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend = RetryingBackend(stubReturning(attempts, noHeaders(StatusCode.Ok): _*), maxRetries = 3, recording(sleeps))
    request.send(backend).code shouldBe StatusCode.Ok
    attempts.get() shouldBe 1
    sleeps shouldBe empty
  }

  it should "retry a 429 and return the subsequent success" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend =
      RetryingBackend(stubReturning(attempts, noHeaders(StatusCode.TooManyRequests, StatusCode.Ok): _*), maxRetries = 3, recording(sleeps))
    request.send(backend).code shouldBe StatusCode.Ok
    attempts.get() shouldBe 2
    sleeps.toList shouldBe List(500L)
  }

  it should "retry 408, 409 and 5xx statuses" in {
    val attempts = new AtomicInteger(0)
    val backend = RetryingBackend(
      stubReturning(
        attempts,
        noHeaders(StatusCode.RequestTimeout, StatusCode.Conflict, StatusCode.InternalServerError, StatusCode.BadGateway, StatusCode.Ok): _*
      ),
      maxRetries = 4,
      _ => ()
    )
    request.send(backend).code shouldBe StatusCode.Ok
    attempts.get() shouldBe 5
  }

  it should "not retry other 4xx responses" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend =
      RetryingBackend(stubReturning(attempts, noHeaders(StatusCode.BadRequest, StatusCode.Ok): _*), maxRetries = 3, recording(sleeps))
    request.send(backend).code shouldBe StatusCode.BadRequest
    attempts.get() shouldBe 1
    sleeps shouldBe empty
  }

  it should "return the last response after exhausting retries" in {
    val attempts = new AtomicInteger(0)
    val backend = RetryingBackend(stubReturning(attempts, noHeaders(StatusCode.InternalServerError): _*), maxRetries = 3, _ => ())
    request.send(backend).code shouldBe StatusCode.InternalServerError
    attempts.get() shouldBe 4
  }

  it should "make exactly one attempt when maxRetries is 0" in {
    val attempts = new AtomicInteger(0)
    val backend =
      RetryingBackend(stubReturning(attempts, noHeaders(StatusCode.TooManyRequests, StatusCode.Ok): _*), maxRetries = 0, _ => ())
    request.send(backend).code shouldBe StatusCode.TooManyRequests
    attempts.get() shouldBe 1
  }

  it should "back off exponentially, capped at 8 seconds" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend = RetryingBackend(stubReturning(attempts, noHeaders(StatusCode.InternalServerError): _*), maxRetries = 6, recording(sleeps))
    request.send(backend): Unit
    sleeps.toList shouldBe List(500L, 1000L, 2000L, 4000L, 8000L, 8000L)
  }

  it should "honor a delta-seconds Retry-After header within the 30-second window" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend = RetryingBackend(
      stubReturning(
        attempts,
        (StatusCode.TooManyRequests, Seq(Header(HeaderNames.RetryAfter, "2"))),
        (StatusCode.TooManyRequests, Seq(Header(HeaderNames.RetryAfter, "30"))),
        (StatusCode.Ok, Seq.empty[Header])
      ),
      maxRetries = 3,
      recording(sleeps)
    )
    request.send(backend).code shouldBe StatusCode.Ok
    sleeps.toList shouldBe List(2000L, 30000L)
  }

  it should "fall back to exponential backoff when Retry-After exceeds the 30-second window" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend = RetryingBackend(
      stubReturning(
        attempts,
        (StatusCode.TooManyRequests, Seq(Header(HeaderNames.RetryAfter, "120"))),
        (StatusCode.TooManyRequests, Seq(Header(HeaderNames.RetryAfter, "9223372036854775807"))),
        (StatusCode.Ok, Seq.empty[Header])
      ),
      maxRetries = 3,
      recording(sleeps)
    )
    request.send(backend).code shouldBe StatusCode.Ok
    sleeps.toList shouldBe List(500L, 1000L)
  }

  it should "fall back to exponential backoff when Retry-After is not delta-seconds" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend = RetryingBackend(
      stubReturning(
        attempts,
        (StatusCode.TooManyRequests, Seq(Header(HeaderNames.RetryAfter, "Wed, 21 Oct 2026 07:28:00 GMT"))),
        (StatusCode.Ok, Seq.empty[Header])
      ),
      maxRetries = 3,
      recording(sleeps)
    )
    request.send(backend).code shouldBe StatusCode.Ok
    sleeps.toList shouldBe List(500L)
  }

  it should "fall back to exponential backoff when Retry-After is negative" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val backend = RetryingBackend(
      stubReturning(
        attempts,
        (StatusCode.TooManyRequests, Seq(Header(HeaderNames.RetryAfter, "-5"))),
        (StatusCode.Ok, Seq.empty[Header])
      ),
      maxRetries = 3,
      recording(sleeps)
    )
    request.send(backend).code shouldBe StatusCode.Ok
    sleeps.toList shouldBe List(500L)
  }

  it should "retry connection failures" in {
    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val stub: SyncBackend = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { req =>
      if (attempts.getAndIncrement() == 0) throw new SttpClientException.ConnectException(req, new java.net.ConnectException("refused"))
      else ResponseStub.adjust("body", StatusCode.Ok)
    }
    request.send(RetryingBackend(stub, maxRetries = 3, recording(sleeps))).code shouldBe StatusCode.Ok
    attempts.get() shouldBe 2
    sleeps.toList shouldBe List(500L)
  }

  it should "throw the last connection failure after exhausting retries" in {
    val attempts = new AtomicInteger(0)
    val stub: SyncBackend = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { req =>
      attempts.getAndIncrement(): Unit
      throw new SttpClientException.ConnectException(req, new java.net.ConnectException("refused"))
    }
    an[SttpClientException.ConnectException] should be thrownBy request.send(RetryingBackend(stub, maxRetries = 1, _ => ()))
    attempts.get() shouldBe 2
  }

  it should "not retry read failures" in {
    val attempts = new AtomicInteger(0)
    val stub: SyncBackend = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { req =>
      attempts.getAndIncrement(): Unit
      throw new SttpClientException.TimeoutException(req, new java.util.concurrent.TimeoutException("read timed out"))
    }
    an[SttpClientException.ReadException] should be thrownBy request.send(RetryingBackend(stub, maxRetries = 3, _ => ()))
    attempts.get() shouldBe 1
  }

  it should "not retry requests whose body cannot be re-sent" in {
    val attempts = new AtomicInteger(0)
    val backend =
      RetryingBackend(stubReturning(attempts, noHeaders(StatusCode.TooManyRequests, StatusCode.Ok): _*), maxRetries = 3, _ => ())
    val streamingBodyRequest = basicRequest.post(uri"http://example.org/test").body(new java.io.ByteArrayInputStream("x".getBytes))
    streamingBodyRequest.send(backend).code shouldBe StatusCode.TooManyRequests
    attempts.get() shouldBe 1
  }

  it should "close an AutoCloseable response body before retrying" in {
    val attempts = new AtomicInteger(0)
    var closed = false
    val errorBody = new java.io.ByteArrayInputStream("busy".getBytes) {
      override def close(): Unit = { closed = true; super.close() }
    }
    val stub: SyncBackend = DefaultSyncBackend.stub.whenAnyRequest.thenRespondF { _ =>
      if (attempts.getAndIncrement() == 0) ResponseStub.exact(errorBody, StatusCode.TooManyRequests)
      else ResponseStub.exact(new java.io.ByteArrayInputStream("ok".getBytes), StatusCode.Ok)
    }
    val response = request.response(asInputStreamAlwaysUnsafe).send(RetryingBackend(stub, maxRetries = 1, _ => ()))
    response.code shouldBe StatusCode.Ok
    closed shouldBe true
  }

  it should "restore the interrupt flag and abort retrying when the default sleep is interrupted" in {
    val attempts = new AtomicInteger(0)
    val backend = RetryingBackend(stubReturning(attempts, noHeaders(StatusCode.TooManyRequests, StatusCode.Ok): _*), maxRetries = 3)
    Thread.currentThread().interrupt()
    try {
      an[InterruptedException] should be thrownBy request.send(backend)
      Thread.currentThread().isInterrupted shouldBe true
      attempts.get() shouldBe 1
    } finally Thread.interrupted(): Unit // clear the flag so it cannot leak into other tests
  }

  it should "retry through asynchronous backends using the effect's sleep" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.{Await, Future}

    val attempts = new AtomicInteger(0)
    val sleeps = ListBuffer[Long]()
    val stub: Backend[Future] = BackendStub.asynchronousFuture.whenAnyRequest.thenRespondF { _ =>
      Future.successful(
        if (attempts.getAndIncrement() == 0) ResponseStub.adjust("busy", StatusCode.TooManyRequests)
        else ResponseStub.adjust("ok", StatusCode.Ok)
      )
    }
    val backend = RetryingBackend[Future](stub, maxRetries = 2, d => Future { sleeps += d.toMillis; () })
    val response = Await.result(request.send(backend), 5.seconds)
    response.code shouldBe StatusCode.Ok
    attempts.get() shouldBe 2
    sleeps.toList shouldBe List(500L)
  }
}
