package sttp.ai.openai.requests.vectorstore.file.batch

object VectorStoreFileBatchRequestBody {

  /** Create a vector store file batch by attaching multiple Files to a vector store.
    *
    * @param fileIds
    *   A list of File IDs that the vector store should use. Useful for tools like file_search that can access files.
    */
  case class CreateVectorStoreFileBatchBody(
      fileIds: Seq[String]
  )

}
