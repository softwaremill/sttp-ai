package sttp.openai.requests.completions.chat

/** @param after
  *   Identifier for the last message from the previous pagination request.
  * @param order
  *   Sort order for messages by timestamp. Use asc for ascending order or desc for descending order. Defaults to asc. Defaults to asc.
  * @param limit
  *   Number of fine-tuning jobs to retrieve. Defaults to 20.
  */
case class ListMessagesQueryParameters(
    after: Option[String] = None,
    order: Option[String] = None,
    limit: Option[Int] = None
) {

  def toMap: Map[String, String] = {
    val queryParams = after.map("after" -> _) ++
      order.map("order" -> _) ++
      limit.map(_.toString).map("limit" -> _)
    queryParams.toMap
  }
}

object ListMessagesQueryParameters {
  val empty: ListMessagesQueryParameters = ListMessagesQueryParameters(None, None, None)
}
