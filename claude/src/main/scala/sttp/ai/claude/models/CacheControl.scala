package sttp.ai.claude.models

import sttp.ai.core.json.SnakePickle
import upickle.implicits.key

@key("type")
sealed trait CacheControl

object CacheControl {
  @key("ephemeral")
  final case class Ephemeral(ttl: Option[String] = None) extends CacheControl

  object Ephemeral {
    implicit val rw: SnakePickle.ReadWriter[Ephemeral] = SnakePickle.macroRW
  }

  implicit val rw: SnakePickle.ReadWriter[CacheControl] = SnakePickle.macroRW
}
