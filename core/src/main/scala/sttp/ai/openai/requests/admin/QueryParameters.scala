package sttp.openai.requests.admin

/** @param after
  *   Identifier for the last job from the previous pagination request.
  * @param order
  *   Defaults to asc.
  * @param limit
  *   Number of fine-tuning jobs to retrieve. Defaults to 20.
  */
case class QueryParameters(
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

object QueryParameters {
  val empty: QueryParameters = QueryParameters(None, None, None)
}
