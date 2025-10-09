package sttp.openai.requests.threads

/** @param limit
  *   Defaults to 20 A limit on the number of objects to be returned. Limit can range between 1 and 100, and the default is 20.
  * @param order
  *   Defaults to desc Sort order by the created_at timestamp of the objects. asc for ascending order and desc for descending order.
  * @param after
  *   A cursor for use in pagination. after is an object ID that defines your place in the list. For instance, if you make a list request
  *   and receive 100 objects, ending with obj_foo, your subsequent call can include after=obj_foo in order to fetch the next page of the
  *   list.
  * @param before
  *   A cursor for use in pagination. before is an object ID that defines your place in the list. For instance, if you make a list request
  *   and receive 100 objects, ending with obj_foo, your subsequent call can include before=obj_foo in order to fetch the previous page of
  *   the list.
  */
case class QueryParameters(
    limit: Option[Int] = None,
    order: Option[String] = None,
    after: Option[String] = None,
    before: Option[String] = None
) {

  def toMap: Map[String, String] = {
    val queryParams = limit.map(_.toString).map("limit" -> _) ++
      order.map("order" -> _) ++
      after.map("after" -> _) ++
      before.map("before" -> _)
    queryParams.toMap
  }
}

object QueryParameters {
  val empty: QueryParameters = QueryParameters(None, None, None, None)
}
