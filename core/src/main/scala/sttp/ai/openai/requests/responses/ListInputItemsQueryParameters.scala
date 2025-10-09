package sttp.openai.requests.responses

/** Query parameters for listing input items for a response.
  *
  * @param after
  *   An item ID to list items after, used in pagination.
  * @param before
  *   An item ID to list items before, used in pagination.
  * @param include
  *   Additional fields to include in the response. See the include parameter for Response creation for more information.
  * @param limit
  *   A limit on the number of objects to be returned. Limit can range between 1 and 100, and the default is 20.
  * @param order
  *   The order to return the input items in. Default is desc. Can be 'asc' or 'desc'.
  */
case class ListInputItemsQueryParameters(
    after: Option[String] = None,
    before: Option[String] = None,
    include: Option[List[String]] = None,
    limit: Option[Int] = None,
    order: Option[String] = None
) {
  def toMap: Map[String, String] = {
    val queryParams = after.map("after" -> _) ++
      before.map("before" -> _) ++
      include.map(list => "include" -> list.mkString(",")) ++
      limit.map(value => "limit" -> value.toString) ++
      order.map("order" -> _)
    queryParams.toMap
  }
}

object ListInputItemsQueryParameters {
  val empty: ListInputItemsQueryParameters = ListInputItemsQueryParameters(None, None, None, None, None)
}
