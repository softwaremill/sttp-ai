package sttp.ai.jev.unit

import io.circe.Json
import io.circe.parser.parse
import sttp.ai.jev.{Choice, JevClient, JevConfig, JevSyncClient, Noul, Score}
import sttp.client4.testing.ResponseStub
import sttp.client4.{DefaultSyncBackend, GenericRequest, StringBody, SyncBackend}
import sttp.model.{Header, StatusCode}

enum Team:
  case Billing, Technical, Sales

/** The quickstart triage request and the verbatim server response to it (answers keyed by position, in a different order than sent). */
object JevFixtures:
  val config: JevConfig = JevConfig("test-key")
  val client: JevClient = JevClient(config)

  val state: String = "Hi, I've been trying to connect my Stripe account for 3 days and the integration keeps failing. Please help ASAP."

  val isUrgent: Noul = Noul(
    "The message conveys urgency or time-sensitivity",
    whenTrue = Some("Explicitly time-sensitive"),
    whenFalse = Some("No urgency expressed")
  )
  val department: Choice[String] = Choice.described(
    "Which team should handle this",
    "billing" -> "Payment or subscription issues",
    "technical" -> "Bugs or integration problems",
    "sales" -> "Pricing or account questions"
  )
  val howFrustrated: Score = Score("How frustrated the customer appears", "Calm, just stating facts", "Frustrated but civil", "Very angry")

  val noulAnswer: String = """{"type":"noul","noul":0.99}"""
  val choiceAnswer: String =
    """{"type":"choice","choice":"technical","confidence":0.71,"probabilities":{"technical":0.81,"billing":0.19,"sales":0.0}}"""
  val scoreAnswer: String =
    """{"type":"score","score":1.0,"confidence":0.99,"legend":{"0":"Calm, just stating facts","1":{"what":"Frustrated but civil","examples":["ugh"]},"2":"Very angry"},"probabilities":{"0":0.0,"1":1.0,"2":0.0}}"""

  /** A 200 body with the given answers under keys `"0"`, `"1"`, ... */
  def responseWith(answers: String*): String =
    responseWithKeyed(answers.zipWithIndex.map((answer, index) => s""""$index":$answer""")*)

  def responseWithKeyed(keyedAnswers: String*): String =
    s"""{"model":"jev-1.13.0","answers":{${keyedAnswers.mkString(",")}},"usage":{"input_tokens":444,"output_tokens":73}}"""

  val triageResponse: String = responseWithKeyed(s""""1":$choiceAnswer""", s""""2":$scoreAnswer""", s""""0":$noulAnswer""")

  def backend(body: String, status: StatusCode = StatusCode.Ok, headers: Seq[Header] = Nil): SyncBackend =
    DefaultSyncBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(body, status, headers))

  def syncClient(body: String, status: StatusCode = StatusCode.Ok): JevSyncClient =
    JevSyncClient(config, backend(body, status))

  def sentJson(request: GenericRequest[?, ?]): Json = request.body match
    case StringBody(text, _, _) => parse(text).fold(throw _, identity)
    case other                  => throw new IllegalArgumentException(s"expected a string body, got $other")
