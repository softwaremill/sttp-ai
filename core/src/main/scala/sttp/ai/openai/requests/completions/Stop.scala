package sttp.openai.requests.completions

import sttp.openai.json.SnakePickle

sealed trait Stop
object Stop {
  implicit val stopW: SnakePickle.Writer[Stop] = SnakePickle
    .writer[ujson.Value]
    .comap[Stop] {
      case SingleStop(value)    => SnakePickle.writeJs(value)
      case MultipleStop(values) => SnakePickle.writeJs(values)
    }

  case class SingleStop(value: String) extends Stop

  case class MultipleStop(values: Seq[String]) extends Stop
}
