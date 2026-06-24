package sttp.ai.openai.requests.caching

sealed trait CacheRetentionPolicy

object CacheRetentionPolicy {
  case object `24H` extends CacheRetentionPolicy
  case object InMemory extends CacheRetentionPolicy
}
