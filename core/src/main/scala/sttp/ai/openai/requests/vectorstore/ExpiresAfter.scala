package sttp.openai.requests.vectorstore

import sttp.openai.json.SnakePickle
import ujson.{Obj, Value}

/** Represents the expiration policy for a vector store.
  *
  * @param anchor
  *   Required. Anchor timestamp after which the expiration policy applies. Supported anchors: last_active_at.
  * @param days
  *   Required. The number of days after the anchor time that the vector store will expire.
  */
case class ExpiresAfter(anchor: String, days: Int)
object ExpiresAfter {

  implicit val expiresAfterRW: SnakePickle.ReadWriter[ExpiresAfter] = SnakePickle
    .readwriter[Value]
    .bimap[ExpiresAfter](
      ea => Obj("anchor" -> ea.anchor, "days" -> ea.days),
      json => ExpiresAfter(json("anchor").str, json("days").num.toInt)
    )
}
