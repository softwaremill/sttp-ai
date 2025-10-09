package sttp.openai.requests.vectorstore.file

import sttp.openai.json.SnakePickle
import ujson.Value

object VectorStoreFileResponseData {

  /** Represents a vector store file.
    *
    * @param id
    *   The identifier, which can be referenced in API endpoints.
    * @param object
    *   The object type, which is always vector_store.file.
    * @param usageBytes
    *   The total vector store usage in bytes. Note that this may be different from the original file size.
    * @param createdAt
    *   The Unix timestamp (in seconds) for when the vector store file was created.
    * @param vectorStoreId
    *   The ID of the vector store that the File is attached to.
    * @param status
    *   The status of the vector store file. Possible values are "in_progress", "completed", "cancelled", or "failed". The status
    *   "completed" indicates that the vector store file is ready for use.
    * @param lastError
    *   The last error associated with this vector store file, or null if there are no errors.
    */
  case class VectorStoreFile(
      id: String,
      `object`: String,
      usageBytes: Int,
      createdAt: Int,
      vectorStoreId: String,
      status: FileStatus,
      lastError: Option[LastError] = None
  )

  object VectorStoreFile {
    implicit val vectorStoreFileR: SnakePickle.Reader[VectorStoreFile] = SnakePickle.macroR[VectorStoreFile]
  }

  /** Represents the last error associated with a vector store file.
    *
    * @param code
    *   The error code. Possible values are "server_error" or "rate_limit_exceeded".
    * @param message
    *   A human-readable description of the error.
    */
  case class LastError(code: ErrorCode, message: String)

  object LastError {
    implicit val lastErrorR: SnakePickle.Reader[LastError] = SnakePickle.macroR[LastError]
  }

  sealed trait ErrorCode
  case object ServerError extends ErrorCode
  case object RateLimitExceeded extends ErrorCode

  object ErrorCode {
    implicit val errorCodeR: SnakePickle.Reader[ErrorCode] = SnakePickle
      .reader[Value]
      .map(json =>
        json.str match {
          case "server_error"        => ServerError
          case "rate_limit_exceeded" => RateLimitExceeded
        }
      )
  }

  /** @param object
    *   Always "list"
    * @param data
    *   A list of vector store file objects.
    * @param firstId
    *   Id of first object
    * @param lastId
    *   Id of last object
    * @param hasMore
    *   Denotes if there are more object available
    */
  case class ListVectorStoreFilesResponse(
      `object`: String = "list",
      data: Seq[VectorStoreFile],
      firstId: String,
      lastId: String,
      hasMore: Boolean
  )

  object ListVectorStoreFilesResponse {
    implicit val listVectorStoreFilesResponseR: SnakePickle.Reader[ListVectorStoreFilesResponse] =
      SnakePickle.macroR[ListVectorStoreFilesResponse]
  }

  /** @param id
    *   Id of deleted object
    * @param `object`
    *   vector_store.file.deleted
    * @param deleted
    *   boolean describing whether or not operation was successful For more information please visit:
    *   [[https://platform.openai.com/docs/api-reference/assistants/deleteAssistant]]
    */
  case class DeleteVectorStoreFileResponse(
      id: String,
      `object`: String,
      deleted: Boolean
  )

  object DeleteVectorStoreFileResponse {
    implicit val deleteVectorStoreFileResponseR: SnakePickle.Reader[DeleteVectorStoreFileResponse] =
      SnakePickle.macroR[DeleteVectorStoreFileResponse]
  }

}
