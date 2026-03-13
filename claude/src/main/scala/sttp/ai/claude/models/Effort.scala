package sttp.ai.claude.models

import sttp.ai.core.json.SnakePickle

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

  implicit val effortRW: SnakePickle.ReadWriter[Effort] = SnakePickle
    .readwriter[ujson.Value]
    .bimap[Effort](
      effort => ujson.Str(effort.value),
      {
        case ujson.Str(LowValue)    => Low
        case ujson.Str(MediumValue) => Medium
        case ujson.Str(HighValue)   => High
        case ujson.Str(MaxValue)    => Max
        case other                  => throw new Exception(s"Unknown Effort: $other")
      }
    )
}
