package sttp.openai.requests.batch

import sttp.openai.json.SnakePickle

/** @param inputFileId
  *   The ID of an uploaded file that contains requests for the new batch. Your input file must be formatted as a JSONL file, and must be
  *   uploaded with the purpose batch. The file can contain up to 50,000 requests, and can be up to 200 MB in size.
  * @param endpoint
  *   The endpoint to be used for all requests in the batch. Currently, /v1/chat/completions, /v1/embeddings, and /v1/completions are
  *   supported. Note that /v1/embeddings batches are also restricted to a maximum of 50,000 embedding inputs across all requests in the
  *   batch.
  * @param completionWindow
  *   The time frame within which the batch should be processed. Currently only 24h is supported.
  * @param metadata
  *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object in
  *   a structured format, and querying for objects via API or the dashboard. Keys are strings with a maximum length of 64 characters.
  *   Values are strings with a maximum length of 512 characters.
  */
case class BatchRequestBody(
    inputFileId: String,
    endpoint: String,
    completionWindow: String,
    metadata: Option[Map[String, String]] = None
)

object BatchRequestBody {
  implicit val batchRequestBodyW: SnakePickle.Writer[BatchRequestBody] = SnakePickle.macroW[BatchRequestBody]
}
