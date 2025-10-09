package sttp.openai.requests.vectorstore.file

import sttp.openai.json.SnakePickle

object VectorStoreFileRequestBody {

  /** Create a vector store file by attaching a File to a vector store.
    *
    * @param fileId
    *   A File ID that the vector store should use. Useful for tools like file_search that can access files.
    */
  case class CreateVectorStoreFileBody(
      fileId: String
  )

  object CreateVectorStoreFileBody {
    implicit val createVectorStoreFileBodyR: SnakePickle.Writer[CreateVectorStoreFileBody] = SnakePickle.macroW[CreateVectorStoreFileBody]
  }

  /** Represents options for listing objects with pagination and filtering.
    *
    * @param limit
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
    * @param filter
    *   Optional. Filter by file status. Possible values are "in_progress", "completed", "failed", "cancelled".
    */
  case class ListVectorStoreFilesBody(
      limit: Int = 20,
      order: String = "desc",
      after: Option[String] = None,
      before: Option[String] = None,
      filter: Option[FileStatus] = None
  ) {
    def toMap: Map[String, String] = {
      val map = Map("limit" -> limit.toString, "order" -> order)
      map ++
        after.map("after" -> _) ++
        before.map("before" -> _) ++
        filter.map("filter" -> _.toString)
    }
  }

  object ListVectorStoreFilesBody {
    implicit val listVectorStoreFilesBodyR: SnakePickle.Writer[ListVectorStoreFilesBody] = SnakePickle.macroW[ListVectorStoreFilesBody]
  }
}
