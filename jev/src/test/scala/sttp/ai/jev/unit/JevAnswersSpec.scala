package sttp.ai.jev.unit

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.jev.JevExceptions.JevException
import sttp.ai.jev.JevExceptions.JevException.DeserializationJevException
import sttp.ai.jev.unit.JevFixtures.*
import sttp.ai.jev.{Answer, Answers, Choice, ChoiceAnswer, ModelInfo, Noul, NoulAnswer, Question, Score, ScoreAnswer, SystemOneResponse}
import sttp.model.Header

class JevAnswersSpec extends AnyFlatSpec with Matchers with EitherValues:

  private def answer[A <: Answer](question: Question[A], body: String): Either[JevException, A] =
    client.ask(state, question).send(backend(body)).body.map(_.answers)

  private def failureMessage[A <: Answer](question: Question[A], body: String): String =
    answer(question, body).left.value match
      case e: DeserializationJevException => e.getMessage
      case other                          => fail(s"expected a deserialization failure, got $other")

  "JevClient" should "match answers to questions by key, whatever their order in the body" in {
    val (urgent, team, frustration) =
      client.ask(state, (isUrgent, department, howFrustrated)).send(backend(triageResponse)).body.value.answers
    urgent.probability shouldBe 0.99
    team.choice shouldBe "technical"
    frustration.score shouldBe 1.0
  }

  it should "key choice probabilities by option name" in {
    answer(department, responseWith(choiceAnswer)).value.probabilities shouldBe Map("billing" -> 0.19, "technical" -> 0.81, "sales" -> 0.0)
  }

  it should "index score probabilities by level, ignoring the legend" in {
    answer(howFrustrated, responseWith(scoreAnswer)).value shouldBe ScoreAnswer(1.0, 0.99, Vector(0, 1, 2), Vector(0.0, 1.0, 0.0))
  }

  it should "decode an enum score to its cases" in {
    answer(typedFrustration, responseWith(scoreAnswer)).value shouldBe
      ScoreAnswer(1.0, 0.99, Vector(Frustration.Calm, Frustration.Civil, Frustration.Angry), Vector(0.0, 1.0, 0.0))
  }

  it should "report the most likely enum level" in {
    answer(typedFrustration, responseWith(scoreAnswer)).value.mostLikely shouldBe Frustration.Civil
  }

  it should "decode an enum choice to its case" in {
    val body =
      responseWith("""{"type":"choice","choice":"Sales","confidence":0.5,"probabilities":{"Billing":0.3,"Technical":0.1,"Sales":0.6}}""")
    answer(Choice.of[Team]("Which team?"), body).value shouldBe
      ChoiceAnswer(Team.Sales, 0.5, Map(Team.Billing -> 0.3, Team.Technical -> 0.1, Team.Sales -> 0.6))
  }

  it should "ignore extra answer keys" in {
    answer(isUrgent, responseWith("""{"type":"noul","noul":0.4,"explanation":"..."}""")).value shouldBe NoulAnswer(0.4)
  }

  it should "return the single answer of a single question" in {
    val response: SystemOneResponse[NoulAnswer] = client.ask(state, isUrgent).send(backend(responseWith(noulAnswer))).body.value
    response.answers shouldBe NoulAnswer(0.99)
  }

  it should "decode model and usage" in {
    val response = client.ask(state, isUrgent).send(backend(responseWith(noulAnswer))).body.value
    response.model shouldBe "jev-1.13.0"
    response.usage.inputTokens shouldBe 444
    response.usage.outputTokens shouldBe 73
  }

  it should "capture the request id of a success" in {
    val headers = Seq(Header("x-typesafe-request-id", "req_0123"))
    client.ask(state, isUrgent).send(backend(responseWith(noulAnswer), headers = headers)).body.value.requestId shouldBe Some("req_0123")
  }

  it should "decode the model list" in {
    val body =
      """{"models":[{"name":"jev-latest","description":"The latest iteration of TypeSafe's System One Model: Jev",
        |"release_date":"2026-09-10T18:38:01.391457+00:00"}]}""".stripMargin
    client.listModels().send(backend(body)).body.value shouldBe
      Seq(ModelInfo("jev-latest", "The latest iteration of TypeSafe's System One Model: Jev", "2026-09-10T18:38:01.391457+00:00"))
  }

  it should "answer a mixed list with a Seq[Answer]" in {
    val answers: Seq[Answer] = syncClient(responseWith(noulAnswer, scoreAnswer)).askAll(state, Seq(isUrgent, howFrustrated)).answers
    answers shouldBe Seq(NoulAnswer(0.99), ScoreAnswer(1.0, 0.99, Vector(0, 1, 2), Vector(0.0, 1.0, 0.0)))
  }

  it should "answer a homogeneous list with the specific answer type" in {
    val answers: Seq[NoulAnswer] = syncClient(responseWith(noulAnswer, noulAnswer)).askAll(state, Seq(isUrgent, Noul("Angry?"))).answers
    answers shouldBe Seq(NoulAnswer(0.99), NoulAnswer(0.99))
  }

  it should "type tuple answers position by position" in {
    summon[Answers[(Noul, Choice[Team], Score[Int])] =:= (NoulAnswer, ChoiceAnswer[Team], ScoreAnswer[Int])]
    succeed
  }

  it should "reject a tuple with a non-question element" in
    assertDoesNotCompile("client.ask(state, (isUrgent, 2))")

  it should "fail with a deserialization error on a malformed 2xx body" in {
    answer(isUrgent, """{"model":"jev-1.13.0"}""").left.value shouldBe a[DeserializationJevException]
  }

  it should "fail on a missing answer" in {
    failureMessage(isUrgent, responseWithKeyed(s""""7":$noulAnswer""")) should include("missing answer for question 0")
  }

  it should "fail when the answer type does not match the question" in {
    failureMessage(howFrustrated, responseWith(noulAnswer)) should include("expected a 'score' answer, got 'noul'")
  }

  it should "fail on a choice outside the options" in {
    val body =
      responseWith("""{"type":"choice","choice":"legal","confidence":0.7,"probabilities":{"billing":0.3,"technical":0.0,"sales":0.7}}""")
    failureMessage(department, body) should include("unknown option 'legal'")
  }

  it should "fail on a probability key outside the options" in {
    val body = responseWith("""{"type":"choice","choice":"sales","confidence":0.7,"probabilities":{"sales":0.9,"legal":0.1}}""")
    failureMessage(department, body) should include("unknown option 'legal'")
  }

  it should "fail on a missing option probability" in {
    val body = responseWith("""{"type":"choice","choice":"sales","confidence":0.7,"probabilities":{"sales":0.9,"billing":0.1}}""")
    failureMessage(department, body) should include("missing probability for option 'technical'")
  }

  it should "fail on a missing level probability" in {
    val body = responseWith("""{"type":"score","score":1.0,"confidence":0.9,"probabilities":{"0":0.0,"1":1.0}}""")
    failureMessage(howFrustrated, body) should include("missing probability for level 2")
  }
