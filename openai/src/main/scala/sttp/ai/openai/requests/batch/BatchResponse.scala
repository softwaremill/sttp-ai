package sttp.ai.openai.requests.batch

/** @param id
  *   The ID of the batch.
  * @param `object`
  *   The object type, which is always batch.
  * @param endpoint
  *   The OpenAI API endpoint used by the batch.
  * @param errors
  *   The errors object.
  * @param inputFileId
  *   The ID of the input file for the batch.
  * @param completionWindow
  *   The time frame within which the batch should be processed.
  * @param status
  *   The current status of the batch.
  * @param outputFileId
  *   The ID of the file containing the outputs of successfully executed requests.
  * @param errorFileId
  *   The ID of the file containing the outputs of requests with errors.
  * @param createdAt
  *   The Unix timestamp (in seconds) for when the batch was created.
  * @param inProgressAt
  *   The Unix timestamp (in seconds) for when the batch started processing.
  * @param expiresAt
  *   The Unix timestamp (in seconds) for when the batch will expire.
  * @param finalizingAt
  *   The Unix timestamp (in seconds) for when the batch started finalizing.
  * @param completedAt
  *   The Unix timestamp (in seconds) for when the batch was completed.
  * @param failedAt
  *   The Unix timestamp (in seconds) for when the batch failed.
  * @param expiredAt
  *   The Unix timestamp (in seconds) for when the batch expired.
  * @param cancellingAt
  *   The Unix timestamp (in seconds) for when the batch started cancelling.
  * @param cancelledAt
  *   The Unix timestamp (in seconds) for when the batch was cancelled.
  * @param requestCounts
  *   The request counts for different statuses within the batch.
  * @param metadata
  *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object in
  *   a structured format, and querying for objects via API or the dashboard. Keys are strings with a maximum length of 64 characters.
  *   Values are strings with a maximum length of 512 characters.
  */
case class BatchResponse(
    id: String,
    `object`: String = "batch",
    endpoint: String,
    errors: Option[Errors],
    inputFileId: String,
    completionWindow: String,
    status: String,
    outputFileId: Option[String],
    errorFileId: Option[String],
    createdAt: Int,
    inProgressAt: Option[Int],
    expiresAt: Option[Int],
    finalizingAt: Option[Int],
    completedAt: Option[Int],
    failedAt: Option[Int],
    expiredAt: Option[Int],
    cancellingAt: Option[Int],
    cancelledAt: Option[Int],
    requestCounts: Option[RequestCounts],
    metadata: Option[Map[String, String]]
)

case class Errors(
    `object`: String = "list",
    data: Seq[Data]
)

/** @param code
  *   An error code identifying the error type.
  * @param message
  *   A human-readable message providing more details about the error.
  * @param param
  *   The name of the parameter that caused the error, if applicable.
  * @param line
  *   The line number of the input file where the error occurred, if applicable.
  */
case class Data(
    code: String,
    message: String,
    param: Option[String] = None,
    line: Option[Int] = None
)

/** @param total
  *   Total number of requests in the batch.
  * @param completed
  *   Number of requests that have been completed successfully.
  * @param failed
  *   Number of requests that have failed.
  */
case class RequestCounts(
    total: Int,
    completed: Int,
    failed: Int
)

case class ListBatchResponse(
    `object`: String = "list",
    data: Seq[BatchResponse],
    firstId: String,
    lastId: String,
    hasMore: Boolean
)
