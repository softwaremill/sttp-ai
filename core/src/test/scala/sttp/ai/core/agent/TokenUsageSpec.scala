package sttp.ai.core.agent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TokenUsageSpec extends AnyFlatSpec with Matchers {

  "Tokens" should "add and compare" in {
    Tokens(3L) + Tokens(4L) shouldBe Tokens(7L)
    Tokens(0L) shouldBe Tokens.Zero
    (Tokens(5L) >= Tokens(5L)) shouldBe true
    (Tokens(4L) >= Tokens(5L)) shouldBe false
    (Tokens(4L) < Tokens(5L)) shouldBe true
    (Tokens(5L) <= Tokens(5L)) shouldBe true
    (Tokens(6L) > Tokens(5L)) shouldBe true
  }

  "TokenUsage" should "accumulate field-wise and derive totalTokens" in {
    val a = TokenUsage(Tokens(10L), Tokens(5L), Tokens(2L), Tokens(1L))
    val b = TokenUsage(Tokens(20L), Tokens(7L), Tokens(3L), Tokens(4L))
    val sum = a + b
    sum shouldBe TokenUsage(Tokens(30L), Tokens(12L), Tokens(5L), Tokens(5L))
    sum.totalTokens shouldBe Tokens(42L)
  }

  it should "treat Zero as identity" in {
    val a = TokenUsage(Tokens(10L), Tokens(5L), Tokens(2L), Tokens(1L))
    a + TokenUsage.Zero shouldBe a
    TokenUsage.Zero + a shouldBe a
    TokenUsage.Zero.totalTokens shouldBe Tokens.Zero
  }
}
