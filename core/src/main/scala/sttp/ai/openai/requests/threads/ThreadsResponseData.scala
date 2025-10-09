package sttp.openai.requests.threads

import sttp.openai.json.SnakePickle

object ThreadsResponseData {

  /** @param id
    *   string The identifier, which can be referenced in API endpoints.
    *
    * @param object
    *   string The object type, which is always thread.
    *
    * @param createdAt
    *   integer The Unix timestamp (in seconds) for when the thread was created.
    *
    * @param metadata
    *   map Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the
    *   object in a structured format. Keys can be a maximum of 64 characters long and values can be a maxium of 512 characters long. For
    *   more information please visit: [[https://platform.openai.com/docs/api-reference/threads/object]]
    */
  case class ThreadData(
      id: String,
      `object`: String = "thread",
      createdAt: Option[Int] = None,
      metadata: Map[String, String] = Map.empty
  )

  object ThreadData {
    implicit val threadDataR: SnakePickle.Reader[ThreadData] = SnakePickle.macroR[ThreadData]
  }

  /** @param id
    * @param `object`
    *   thread.deleted
    * @param deleted
    */
  case class DeleteThreadResponse(
      id: String,
      `object`: String,
      deleted: Boolean
  )

  object DeleteThreadResponse {
    implicit val deleteThreadResponseReadWriter: SnakePickle.ReadWriter[DeleteThreadResponse] = SnakePickle.macroRW[DeleteThreadResponse]
  }
}
