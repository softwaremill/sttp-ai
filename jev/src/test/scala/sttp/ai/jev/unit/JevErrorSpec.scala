package sttp.ai.jev.unit

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.jev.JevExceptions.JevException
import sttp.ai.jev.JevExceptions.JevException.*
import sttp.ai.jev.unit.JevFixtures.*
import sttp.model.{Header, StatusCode}

class JevErrorSpec extends AnyFlatSpec with Matchers with EitherValues:

  private def error(body: String, status: StatusCode, headers: Seq[Header] = Nil): JevException =
    client.ask(state, isUrgent).send(backend(body, status, headers)).body.left.value

  "JevClient" should "map a 400 with a string detail" in {
    val exception = error("""{"detail":"Too many score levels. Must have at most 10 levels."}""", StatusCode.BadRequest)
    exception shouldBe an[InvalidRequestException]
    exception.getMessage shouldBe "Too many score levels. Must have at most 10 levels."
    exception.`type` shouldBe None
  }

  it should "map a 400 with an object detail, keeping the error type" in {
    val exception = error("""{"detail":{"error_type":"api_usage_error","message":"Unknown model: jev"}}""", StatusCode.BadRequest)
    exception shouldBe an[InvalidRequestException]
    exception.getMessage shouldBe "Unknown model: jev"
    exception.`type` shouldBe Some("api_usage_error")
  }

  it should "format a 422 validation list by question position" in {
    val body =
      """{"detail":[{"type":"missing","loc":["body","model"],"msg":"Field required","input":{}},
        |{"type":"missing","loc":["body","questions","0","score","criteria"],"msg":"Field required","input":{"type":"score"}}]}""".stripMargin
    val exception = error(body, StatusCode.UnprocessableEntity)
    exception shouldBe an[InvalidRequestException]
    exception.getMessage shouldBe "model: Field required; questions.0.score.criteria: Field required"
  }

  it should "map 401 and 403 to an authentication failure" in {
    val body = """{"detail":{"error_type":"authentication_error","message":"Cannot authenticate with the server."}}"""
    error(body, StatusCode.Unauthorized) shouldBe an[AuthenticationException]
    error(body, StatusCode.Forbidden) shouldBe an[AuthenticationException]
  }

  it should "map a 429 to a rate limit failure" in {
    error("""{"detail":{"error_type":"rate_limit_error","message":"Rate limit exceeded"}}""", StatusCode.TooManyRequests) shouldBe
      a[RateLimitException]
  }

  it should "map a 529 to an overloaded failure" in {
    error("""{"detail":{"error_type":"overloaded_error","message":"Overloaded"}}""", StatusCode(529)) shouldBe an[OverloadedException]
  }

  it should "map any other status to an API failure" in {
    error("""{"detail":{"error_type":"internal_error","message":"Internal error"}}""", StatusCode.InternalServerError) shouldBe
      an[APIException]
  }

  it should "use an unparseable error body as the message" in {
    error("<html>bad gateway</html>", StatusCode.BadGateway).getMessage shouldBe "<html>bad gateway</html>"
  }

  it should "capture the request id of an error" in {
    val requestId = Header("x-typesafe-request-id", "req_0123")
    error("""{"detail":"nope"}""", StatusCode.BadRequest, Seq(requestId)).requestId shouldBe Some("req_0123")
  }

  "JevSyncClient" should "throw the mapped exception" in {
    val body = """{"detail":{"error_type":"authentication_error","message":"Cannot authenticate with the server."}}"""
    an[AuthenticationException] should be thrownBy syncClient(body, StatusCode.Unauthorized).ask(state, isUrgent)
  }
