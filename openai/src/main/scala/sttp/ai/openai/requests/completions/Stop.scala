package sttp.ai.openai.requests.completions

sealed trait Stop
object Stop {
  case class SingleStop(value: String) extends Stop

  case class MultipleStop(values: Seq[String]) extends Stop
}
