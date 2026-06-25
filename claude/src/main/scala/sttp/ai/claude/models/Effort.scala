package sttp.ai.claude.models

sealed abstract class Effort(val value: String)

object Effort {

  private val LowValue = "low"
  private val MediumValue = "medium"
  private val HighValue = "high"
  private val MaxValue = "max"

  case object Low extends Effort(LowValue)
  case object Medium extends Effort(MediumValue)
  case object High extends Effort(HighValue)
  case object Max extends Effort(MaxValue)
}
