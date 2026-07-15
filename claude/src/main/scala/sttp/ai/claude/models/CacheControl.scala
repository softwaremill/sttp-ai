package sttp.ai.claude.models

sealed trait CacheControl

object CacheControl {
  final case class Ephemeral(ttl: Option[String] = None) extends CacheControl
}
