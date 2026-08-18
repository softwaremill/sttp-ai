package sttp.ai.openai.requests.responses

import io.circe.Json

/** A single server-sent event emitted by a streamed Responses API call.
  *
  * [[https://developers.openai.com/api/reference/resources/responses/streaming-events]]
  *
  * Each case is named after its wire `type` with the `response.` prefix dropped and the remaining dots turned into camel-case boundaries,
  * e.g. `response.output_text.delta` becomes [[ResponsesStreamEvent.OutputTextDelta]]. The decoder relies on that correspondence, so a new
  * case must keep it (see `OpenAIManualCodecs.responsesStreamEventDispatch`).
  *
  * Decode-only: these events are only ever received, never sent. A `type` this version of the library does not model decodes to
  * [[ResponsesStreamEvent.Unknown]] rather than failing the stream.
  */
sealed trait ResponsesStreamEvent {

  /** The event type as sent by OpenAI, e.g. `response.output_text.delta`. */
  def `type`: String

  /** Monotonically increasing sequence number of this event within the stream. */
  def sequenceNumber: Int

  /** Token usage for the response so far, if this event carries it.
    *
    * The Responses API reports usage on the response-lifecycle events only - as part of their [[ResponsesResponseBody]] snapshot - and it
    * is populated once the response reaches a terminal state, so in practice you read it off `response.completed` (or `response.incomplete`
    * / `response.failed`). Unlike Chat Completions there is no `include_usage` option to opt into: usage is always reported. Every other
    * event returns `None`.
    *
    * To take the usage of a streamed response, keep the last value seen: `events.flatMap(_.usage).lastOption`.
    */
  def usage: Option[ResponsesResponseBody.Usage] = None
}

object ResponsesStreamEvent {

  /** The `[DONE]` sentinel. The Responses API terminates a stream with `response.completed` / `response.failed` / `response.incomplete`
    * followed by the connection closing, and is not documented to emit a sentinel frame - but the official SDKs skip `data: [DONE]` on
    * every stream, so the streaming modules do too, for OpenAI-compatible providers that send one.
    */
  val DoneEventMessage: String = "[DONE]"

  /** Whether an SSE frame's `data` payload is an event to decode, as opposed to a keep-alive frame or the [[DoneEventMessage]] sentinel.
    *
    * Deliberately matches on `data` alone rather than comparing whole `ServerSentEvent`s the way the chat-completions modules do: the
    * Responses API sets an `event:` line on every frame, so a sentinel frame would carry an event type and whole-object equality would miss
    * it, letting `[DONE]` reach the JSON decoder and fail the stream.
    */
  def isEventData(data: String): Boolean = {
    val trimmed = data.trim
    trimmed.nonEmpty && trimmed != DoneEventMessage
  }

  // --- payload types specific to streaming events ---

  /** A top log-probability entry of a streaming [[LogProb]]. */
  case class TopLogProb(token: Option[String] = None, logprob: Option[Double] = None)

  /** A streaming log-probability entry.
    *
    * Deliberately not [[ResponsesResponseBody.OutputContent.LogProb]]: the streamed shape carries no `bytes`, and its `top_logprobs`
    * entries have optional `token` / `logprob`.
    */
  case class LogProb(token: String, logprob: Double, topLogprobs: List[TopLogProb] = Nil)

  /** Incremental `stdout` / `stderr` of a shell tool call. */
  case class ShellOutputDelta(stdout: Option[String] = None, stderr: Option[String] = None)

  /** How a shell tool call finished. */
  sealed trait ShellOutcome

  object ShellOutcome {
    case class Timeout() extends ShellOutcome
    case class Exit(exitCode: Int) extends ShellOutcome
  }

  /** The completed output of a single shell tool call command. */
  case class ShellOutput(outcome: ShellOutcome, stderr: String, stdout: String, createdBy: Option[String] = None)

  // --- response lifecycle ---

  case class Created(response: ResponsesResponseBody, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.created"
    override def usage: Option[ResponsesResponseBody.Usage] = response.usage
  }

  case class InProgress(response: ResponsesResponseBody, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.in_progress"
    override def usage: Option[ResponsesResponseBody.Usage] = response.usage
  }

  case class Completed(response: ResponsesResponseBody, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.completed"
    override def usage: Option[ResponsesResponseBody.Usage] = response.usage
  }

  case class Failed(response: ResponsesResponseBody, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.failed"
    override def usage: Option[ResponsesResponseBody.Usage] = response.usage
  }

  case class Incomplete(response: ResponsesResponseBody, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.incomplete"
    override def usage: Option[ResponsesResponseBody.Usage] = response.usage
  }

  case class Queued(response: ResponsesResponseBody, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.queued"
    override def usage: Option[ResponsesResponseBody.Usage] = response.usage
  }

  // --- output items ---

  case class OutputItemAdded(item: ResponsesResponseBody.OutputItem, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.output_item.added"
  }

  case class OutputItemDone(item: ResponsesResponseBody.OutputItem, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.output_item.done"
  }

  // --- content parts ---

  case class ContentPartAdded(
      contentIndex: Int,
      itemId: String,
      outputIndex: Int,
      part: ResponsesResponseBody.OutputContent,
      sequenceNumber: Int
  ) extends ResponsesStreamEvent {
    val `type`: String = "response.content_part.added"
  }

  case class ContentPartDone(
      contentIndex: Int,
      itemId: String,
      outputIndex: Int,
      part: ResponsesResponseBody.OutputContent,
      sequenceNumber: Int
  ) extends ResponsesStreamEvent {
    val `type`: String = "response.content_part.done"
  }

  // --- output text ---

  case class OutputTextDelta(
      contentIndex: Int,
      delta: String,
      itemId: String,
      outputIndex: Int,
      sequenceNumber: Int,
      logprobs: List[LogProb] = Nil
  ) extends ResponsesStreamEvent {
    val `type`: String = "response.output_text.delta"
  }

  case class OutputTextDone(
      contentIndex: Int,
      itemId: String,
      outputIndex: Int,
      sequenceNumber: Int,
      text: String,
      logprobs: List[LogProb] = Nil
  ) extends ResponsesStreamEvent {
    val `type`: String = "response.output_text.done"
  }

  case class OutputTextAnnotationAdded(
      annotationIndex: Int,
      contentIndex: Int,
      itemId: String,
      outputIndex: Int,
      sequenceNumber: Int,
      annotation: Option[ResponsesResponseBody.OutputContent.Annotation] = None
  ) extends ResponsesStreamEvent {
    val `type`: String = "response.output_text.annotation.added"
  }

  // --- refusals ---

  case class RefusalDelta(contentIndex: Int, delta: String, itemId: String, outputIndex: Int, sequenceNumber: Int)
      extends ResponsesStreamEvent {
    val `type`: String = "response.refusal.delta"
  }

  case class RefusalDone(contentIndex: Int, itemId: String, outputIndex: Int, refusal: String, sequenceNumber: Int)
      extends ResponsesStreamEvent {
    val `type`: String = "response.refusal.done"
  }

  // --- reasoning ---

  case class ReasoningTextDelta(contentIndex: Int, delta: String, itemId: String, outputIndex: Int, sequenceNumber: Int)
      extends ResponsesStreamEvent {
    val `type`: String = "response.reasoning_text.delta"
  }

  case class ReasoningTextDone(contentIndex: Int, itemId: String, outputIndex: Int, sequenceNumber: Int, text: String)
      extends ResponsesStreamEvent {
    val `type`: String = "response.reasoning_text.done"
  }

  case class ReasoningSummaryTextDelta(delta: String, itemId: String, outputIndex: Int, sequenceNumber: Int, summaryIndex: Int)
      extends ResponsesStreamEvent {
    val `type`: String = "response.reasoning_summary_text.delta"
  }

  case class ReasoningSummaryTextDone(itemId: String, outputIndex: Int, sequenceNumber: Int, summaryIndex: Int, text: String)
      extends ResponsesStreamEvent {
    val `type`: String = "response.reasoning_summary_text.done"
  }

  case class ReasoningSummaryPartAdded(
      itemId: String,
      outputIndex: Int,
      part: ResponsesResponseBody.OutputItem.SummaryText,
      sequenceNumber: Int,
      summaryIndex: Int
  ) extends ResponsesStreamEvent {
    val `type`: String = "response.reasoning_summary_part.added"
  }

  case class ReasoningSummaryPartDone(
      itemId: String,
      outputIndex: Int,
      part: ResponsesResponseBody.OutputItem.SummaryText,
      sequenceNumber: Int,
      summaryIndex: Int,
      status: Option[String] = None
  ) extends ResponsesStreamEvent {
    val `type`: String = "response.reasoning_summary_part.done"
  }

  // --- function calls ---

  case class FunctionCallArgumentsDelta(delta: String, itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.function_call_arguments.delta"
  }

  case class FunctionCallArgumentsDone(arguments: String, itemId: String, outputIndex: Int, sequenceNumber: Int)
      extends ResponsesStreamEvent {
    val `type`: String = "response.function_call_arguments.done"
  }

  // --- file search ---

  case class FileSearchCallInProgress(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.file_search_call.in_progress"
  }

  case class FileSearchCallSearching(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.file_search_call.searching"
  }

  case class FileSearchCallCompleted(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.file_search_call.completed"
  }

  // --- web search ---

  case class WebSearchCallInProgress(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.web_search_call.in_progress"
  }

  case class WebSearchCallSearching(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.web_search_call.searching"
  }

  case class WebSearchCallCompleted(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.web_search_call.completed"
  }

  // --- code interpreter ---

  case class CodeInterpreterCallInProgress(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.code_interpreter_call.in_progress"
  }

  case class CodeInterpreterCallInterpreting(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.code_interpreter_call.interpreting"
  }

  case class CodeInterpreterCallCompleted(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.code_interpreter_call.completed"
  }

  case class CodeInterpreterCallCodeDelta(delta: String, itemId: String, outputIndex: Int, sequenceNumber: Int)
      extends ResponsesStreamEvent {
    val `type`: String = "response.code_interpreter_call_code.delta"
  }

  case class CodeInterpreterCallCodeDone(code: String, itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.code_interpreter_call_code.done"
  }

  // --- image generation ---

  case class ImageGenerationCallInProgress(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.image_generation_call.in_progress"
  }

  case class ImageGenerationCallGenerating(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.image_generation_call.generating"
  }

  case class ImageGenerationCallCompleted(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.image_generation_call.completed"
  }

  case class ImageGenerationCallPartialImage(
      itemId: String,
      outputIndex: Int,
      partialImageB64: String,
      partialImageIndex: Int,
      sequenceNumber: Int,
      background: Option[String] = None,
      outputFormat: Option[String] = None,
      quality: Option[String] = None,
      size: Option[String] = None
  ) extends ResponsesStreamEvent {
    val `type`: String = "response.image_generation_call.partial_image"
  }

  // --- MCP ---

  case class McpCallInProgress(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.mcp_call.in_progress"
  }

  case class McpCallCompleted(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.mcp_call.completed"
  }

  case class McpCallFailed(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.mcp_call.failed"
  }

  case class McpCallArgumentsDelta(delta: String, itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.mcp_call_arguments.delta"
  }

  case class McpCallArgumentsDone(arguments: String, itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.mcp_call_arguments.done"
  }

  case class McpListToolsInProgress(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.mcp_list_tools.in_progress"
  }

  case class McpListToolsCompleted(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.mcp_list_tools.completed"
  }

  case class McpListToolsFailed(itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.mcp_list_tools.failed"
  }

  // --- custom tools ---

  case class CustomToolCallInputDelta(delta: String, itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.custom_tool_call_input.delta"
  }

  case class CustomToolCallInputDone(input: String, itemId: String, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.custom_tool_call_input.done"
  }

  // --- shell tool ---

  case class ShellCallCommandAdded(command: String, commandIndex: Int, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.shell_call_command.added"
  }

  case class ShellCallCommandDelta(
      commandIndex: Int,
      delta: String,
      outputIndex: Int,
      sequenceNumber: Int,
      obfuscation: Option[String] = None
  ) extends ResponsesStreamEvent {
    val `type`: String = "response.shell_call_command.delta"
  }

  case class ShellCallCommandDone(command: String, commandIndex: Int, outputIndex: Int, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.shell_call_command.done"
  }

  case class ShellCallOutputContentDelta(
      commandIndex: Int,
      delta: ShellOutputDelta,
      itemId: String,
      outputIndex: Int,
      sequenceNumber: Int
  ) extends ResponsesStreamEvent {
    val `type`: String = "response.shell_call_output_content.delta"
  }

  case class ShellCallOutputContentDone(
      commandIndex: Int,
      itemId: String,
      output: List[ShellOutput],
      outputIndex: Int,
      sequenceNumber: Int
  ) extends ResponsesStreamEvent {
    val `type`: String = "response.shell_call_output_content.done"
  }

  // --- audio ---

  case class AudioDelta(delta: String, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.audio.delta"
  }

  case class AudioDone(sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.audio.done"
  }

  case class AudioTranscriptDelta(delta: String, sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.audio.transcript.delta"
  }

  case class AudioTranscriptDone(sequenceNumber: Int) extends ResponsesStreamEvent {
    val `type`: String = "response.audio.transcript.done"
  }

  // --- error ---

  /** An error raised while the response was being generated. Note the wire `type` is the bare `error`, not `response.error`. */
  case class Error(message: String, sequenceNumber: Int, code: Option[String] = None, param: Option[String] = None)
      extends ResponsesStreamEvent {
    val `type`: String = "error"
  }

  // --- forward compatibility ---

  /** An event whose `type` this version of the library does not model, e.g. one introduced by OpenAI after this release. Carries the
    * verbatim frame so it can be inspected or logged; `sequenceNumber` is `-1` if the frame carried none.
    */
  case class Unknown(`type`: String, raw: Json) extends ResponsesStreamEvent {
    def sequenceNumber: Int = raw.hcursor.get[Int]("sequence_number").getOrElse(-1)
  }

  /** Every `type` this version of the library models. The decoder routes anything else to [[Unknown]]; a new case must be added here too.
    */
  val KnownTypes: Set[String] = Set(
    "response.created",
    "response.in_progress",
    "response.completed",
    "response.failed",
    "response.incomplete",
    "response.queued",
    "response.output_item.added",
    "response.output_item.done",
    "response.content_part.added",
    "response.content_part.done",
    "response.output_text.delta",
    "response.output_text.done",
    "response.output_text.annotation.added",
    "response.refusal.delta",
    "response.refusal.done",
    "response.reasoning_text.delta",
    "response.reasoning_text.done",
    "response.reasoning_summary_text.delta",
    "response.reasoning_summary_text.done",
    "response.reasoning_summary_part.added",
    "response.reasoning_summary_part.done",
    "response.function_call_arguments.delta",
    "response.function_call_arguments.done",
    "response.file_search_call.in_progress",
    "response.file_search_call.searching",
    "response.file_search_call.completed",
    "response.web_search_call.in_progress",
    "response.web_search_call.searching",
    "response.web_search_call.completed",
    "response.code_interpreter_call.in_progress",
    "response.code_interpreter_call.interpreting",
    "response.code_interpreter_call.completed",
    "response.code_interpreter_call_code.delta",
    "response.code_interpreter_call_code.done",
    "response.image_generation_call.in_progress",
    "response.image_generation_call.generating",
    "response.image_generation_call.completed",
    "response.image_generation_call.partial_image",
    "response.mcp_call.in_progress",
    "response.mcp_call.completed",
    "response.mcp_call.failed",
    "response.mcp_call_arguments.delta",
    "response.mcp_call_arguments.done",
    "response.mcp_list_tools.in_progress",
    "response.mcp_list_tools.completed",
    "response.mcp_list_tools.failed",
    "response.custom_tool_call_input.delta",
    "response.custom_tool_call_input.done",
    "response.shell_call_command.added",
    "response.shell_call_command.delta",
    "response.shell_call_command.done",
    "response.shell_call_output_content.delta",
    "response.shell_call_output_content.done",
    "response.audio.delta",
    "response.audio.done",
    "response.audio.transcript.delta",
    "response.audio.transcript.done",
    "error"
  )
}
