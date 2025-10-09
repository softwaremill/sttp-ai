package sttp.openai.requests.finetuning

import sttp.openai.json.SnakePickle
import ujson.Str

abstract class Type(val value: String)

object Type {
  implicit def typeRW(implicit byTypeValue: Map[String, Type]): SnakePickle.ReadWriter[Type] = SnakePickle
    .readwriter[ujson.Value]
    .bimap[Type](
      `type` => SnakePickle.writeJs(`type`.value),
      jsonValue =>
        SnakePickle.read[ujson.Value](jsonValue) match {
          case Str(value) =>
            byTypeValue.get(value) match {
              case Some(t) => t
              case None    => throw new Exception(s"Could not deserialize: $value")
            }
          case e => throw new Exception(s"Could not deserialize: $e")
        }
    )
}
