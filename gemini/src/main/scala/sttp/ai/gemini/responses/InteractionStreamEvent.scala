package sttp.ai.gemini.responses

import sttp.ai.gemini.models.Usage

/** SSE event envelope for streamed interactions.
  *
  * `eventType` is deliberately an open String, not an enum: the API documents `interaction.completed` and `error`, but emits additional
  * incremental event types; unknown types must not fail deserialization. Match on the value and ignore what you don't handle.
  */
case class InteractionStreamEvent(
    eventType: String,
    eventId: Option[String] = None,
    interaction: Option[InteractionResponse] = None,
    error: Option[StreamError] = None,
    metadata: Option[StreamMetadata] = None
)

case class StreamError(code: Option[String] = None, message: Option[String] = None)

case class StreamMetadata(totalUsage: Option[Usage] = None)
