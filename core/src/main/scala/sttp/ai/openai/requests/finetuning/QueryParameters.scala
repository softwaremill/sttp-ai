package sttp.openai.requests.finetuning

/** @param after
  *   Identifier for the last job from the previous pagination request.
  * @param limit
  *   Number of fine-tuning jobs to retrieve.
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
