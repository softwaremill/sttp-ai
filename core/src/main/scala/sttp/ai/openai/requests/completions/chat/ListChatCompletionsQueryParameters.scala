package sttp.openai.requests.completions.chat

import sttp.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel

/** @param model
  *   The model used to generate the chat completions.
  * @param metadata
  *   A list of metadata keys to filter the chat completions by. Example: metadata[key1]=value1&metadata[key2]=value2
  * @param after
  *   Identifier for the last chat completion from the previous pagination request.
  * @param limit
  *   Number of chat completions to retrieve. Defaults to 20.
  * @param order
  *   Sort order for chat completions by timestamp. Use asc for ascending order or desc for descending order. Defaults to asc.
  */
case class ListChatCompletionsQueryParameters(
    model: Option[ChatCompletionModel] = None,
    metadata: Option[Map[String, String]] = None,
    after: Option[String] = None,
    limit: Option[Int] = None,
    order: Option[String] = None
) {

  def toMap: Map[String, String] = {
    val queryParams = model.map("model" -> _.value) ++
      metadata.map(_.map { case (k, v) => s"metadata[$k]" -> v }).getOrElse(Map.empty) ++
      after.map("after" -> _) ++
      limit.map(_.toString).map("limit" -> _) ++
      order.map("order" -> _)
    queryParams.toMap
  }
}

object ListChatCompletionsQueryParameters {
  val empty: ListChatCompletionsQueryParameters = ListChatCompletionsQueryParameters(None, None, None, None, None)
}
