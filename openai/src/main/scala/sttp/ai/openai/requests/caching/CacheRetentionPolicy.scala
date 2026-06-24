package sttp.ai.openai.requests.caching
import sttp.ai.core.json.SnakePickle

sealed trait CacheRetentionPolicy

object CacheRetentionPolicy {
  case object `24H` extends CacheRetentionPolicy
  case object InMemory extends CacheRetentionPolicy

  implicit val writer: SnakePickle.Writer[CacheRetentionPolicy] = SnakePickle.writer[String].comap {
    case `24H`    => "24h"
    case InMemory => "in_memory"
  }

  implicit val reader: SnakePickle.Reader[CacheRetentionPolicy] = SnakePickle.reader[String].map {
    case "24h"       => `24H`
    case "in_memory" => InMemory
    case other       => throw new IllegalArgumentException(s"Unknown cache retention policy: $other")
  }
}
