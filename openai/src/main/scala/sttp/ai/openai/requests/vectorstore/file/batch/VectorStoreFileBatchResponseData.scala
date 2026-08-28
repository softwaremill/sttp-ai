package sttp.ai.openai.requests.vectorstore.file.batch

import sttp.ai.openai.requests.vectorstore.VectorStoreResponseData.FileCounts
import sttp.ai.openai.requests.vectorstore.file.FileStatus

object VectorStoreFileBatchResponseData {

  /** Represents a batch of files attached to a vector store.
    *
    * @param id
    *   The identifier, which can be referenced in API endpoints.
    * @param object
    *   The object type. The live API returns vector_store.file_batch (observed 2026-08), while the OpenAPI spec's enum lists
    *   vector_store.files_batch; treat this as an opaque string.
    * @param createdAt
    *   The Unix timestamp (in seconds) for when the vector store files batch was created.
    * @param vectorStoreId
    *   The ID of the vector store that the Files are attached to.
    * @param status
    *   The status of the vector store files batch. Possible values are "in_progress", "completed", "cancelled", or "failed".
    * @param fileCounts
    *   Number of files in the batch in each status.
    */
  case class VectorStoreFileBatch(
      id: String,
      `object`: String,
      createdAt: Int,
      vectorStoreId: String,
      status: FileStatus,
      fileCounts: FileCounts
  )

}
