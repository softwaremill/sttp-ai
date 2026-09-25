package sttp.ai.jev.unit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.jev.{Choice, ScoreAnswer}

enum Severity:
  case Low
  case Other(reason: String)

class QuestionSpec extends AnyFlatSpec with Matchers:

  "Choice" should "reject duplicate option names" in {
    an[IllegalArgumentException] should be thrownBy Choice.described("Which?", "a" -> "first", "a" -> "second")
  }

  it should "drop duplicate strings" in {
    Choice.strings("Which?", Seq("a", "a")).options.map(_.name) shouldBe Vector("a")
  }

  it should "not derive options from an enum with a parameterised case" in
    assertDoesNotCompile("""Choice.of[Severity]("How severe?")""")

  "ScoreAnswer" should "report the most probable level as mostLikely" in {
    ScoreAnswer(1.4, 0.5, Vector(0.1, 0.3, 0.6)).mostLikely shouldBe 2
  }

  it should "pick the lowest level on a tie" in {
    ScoreAnswer(0.8, 0.4, Vector(0.4, 0.4, 0.2)).mostLikely shouldBe 0
  }
