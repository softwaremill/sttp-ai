package sttp.openai.requests.vectorstore

import sttp.openai.json.SnakePickle

object VectorStoreRequestBody {

  /** Represents options for creating vector store.
    *
    * @param fileIds
    *   Optional. A list of File IDs that the vector store should use. Useful for tools like file_search that can access files.
    * @param name
    *   Optional. The name of the vector store.
    * @param expiresAfter
    *   Optional. The expiration policy for a vector store.
    * @param metadata
    *   Optional. Set of 16 key-value pairs that can be attached to an object. Useful for storing additional information about the object in
    *   a structured format. Keys can be a maximum of 64 characters long and values can be a maximum of 512 characters long.
    */
  case class CreateVectorStoreBody(
      name: Option[String] = None,
      fileIds: Option[Seq[String]] = None,
      expiresAfter: Option[ExpiresAfter] = None,
      metadata: Option[Map[String, String]] = None
  )

  object CreateVectorStoreBody {
    implicit val createVectorStoreBodyW: SnakePickle.Writer[CreateVectorStoreBody] = SnakePickle.macroW[CreateVectorStoreBody]
  }

  /** Represents options for modifying vector store.
    * @param name
    *   Optional. The name of the vector store.
    * @param expiresAfter
    *   Optional. The expiration policy for a vector store.
    * @param metadata
    *   Optional. Set of 16 key-value pairs that can be attached to an object. Useful for storing additional information about the object in
    *   a structured format. Keys can be a maximum of 64 characters long and values can be a maximum of 512 characters long.
    */
  case class ModifyVectorStoreBody(
      name: Option[String] = None,
      expiresAfter: Option[ExpiresAfter] = None,
      metadata: Option[Map[String, String]] = None
  )

  object ModifyVectorStoreBody {
    implicit val modifyVectorStoreBody: SnakePickle.Writer[ModifyVectorStoreBody] = SnakePickle.macroW[ModifyVectorStoreBody]
  }

}
