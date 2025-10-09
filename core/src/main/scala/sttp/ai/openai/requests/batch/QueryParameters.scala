package sttp.openai.requests.batch

/** @param after
  *   A cursor for use in pagination. after is an object ID that defines your place in the list. For instance, if you make a list request
  *   and receive 100 objects, ending with obj_foo, your subsequent call can include after=obj_foo in order to fetch the next page of the
  *   list.
  * @param limit
  *   A limit on the number of objects to be returned. Limit can range between 1 and 100, and the default is 20.
  */
case class QueryParameters(
    after: Option[String] = None,
    limit: Option[Int] = None
) {

  def toMap: Map[String, String] = {
    val queryParams = after.map("after" -> _) ++
      limit.map(_.toString).map("limit" -> _)
    queryParams.toMap
  }
}

object QueryParameters {
  val empty: QueryParameters = QueryParameters(None, None)
}
