package sttp.ai.openai.requests.vectorstore

/** Represents the expiration policy for a vector store.
  *
  * @param anchor
  *   Required. Anchor timestamp after which the expiration policy applies. Supported anchors: last_active_at.
  * @param days
  *   Required. The number of days after the anchor time that the vector store will expire.
  */
case class ExpiresAfter(anchor: String, days: Int)
