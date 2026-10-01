package sttp.ai.openai.requests.responses

import sttp.ai.openai.requests.caching.CacheRetentionPolicy
import sttp.ai.openai.requests.responses.ResponsesRequestBody.Input

/** Request body for compacting a conversation on demand.
  *
  * @param model
  *   Model ID used to compact the conversation.
  * @param input
  *   The conversation to compact.
  * @param instructions
  *   A system (or developer) message inserted into the model's context.
  * @param previousResponseId
  *   The ID of a stored response whose conversation should be compacted.
  * @param promptCacheKey
  *   Used by OpenAI to cache responses for similar requests to optimize your cache hit rates.
  * @param promptCacheRetention
  *   How long to retain a prompt cache entry created by this request.
  * @param serviceTier
  *   Specifies the processing type used for serving the request. Defaults to 'auto'.
  */
case class CompactRequestBody(
    model: ResponsesModel,
    input: Option[Either[String, List[Input]]] = None,
    instructions: Option[String] = None,
    previousResponseId: Option[String] = None,
    promptCacheKey: Option[String] = None,
    promptCacheRetention: Option[CacheRetentionPolicy] = None,
    serviceTier: Option[String] = None
)
