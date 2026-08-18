package sttp.ai.openai.fixtures

/** JSON frames for the streamed Responses API events, in the shape OpenAI sends them.
  *
  * [[minimalByType]] holds one minimal-but-valid frame per modelled event type; `ResponsesStreamEventDataSpec` walks
  * `ResponsesStreamEvent.KnownTypes` against it, which is what keeps the model, the decoder and this fixture from drifting apart.
  */
object ResponsesStreamEventFixture {

  /** Every frame below carries this `sequence_number`, so specs can assert the wiring uniformly. */
  val SequenceNumber: Int = 1

  private val itemId = """"item_id":"item_1""""
  private val outputIndex = """"output_index":0"""
  private val contentIndex = """"content_index":0"""
  private val summaryIndex = """"summary_index":0"""
  private val commandIndex = """"command_index":0"""

  private def frame(eventType: String, fields: String*): String =
    (Seq(s""""type":"$eventType"""", s""""sequence_number":$SequenceNumber""") ++ fields).mkString("{", ",", "}")

  /** A `function_call` output item, as carried by `response.output_item.added` / `.done`. */
  val jsonFunctionCallItem: String =
    """{"type":"function_call","arguments":"{}","call_id":"call_1","name":"get_weather","id":"fc_1","status":"completed"}"""

  /** A `custom_tool_call` output item - modelled only since streaming support was added. */
  val jsonCustomToolCallItem: String =
    """{"type":"custom_tool_call","call_id":"call_2","input":"echo hi","name":"shell","id":"ctc_1"}"""

  /** A `shell_call` output item: a real item type this library does not model, so it must decode to `OutputItem.Unknown`. */
  val jsonUnknownItem: String =
    """{"type":"shell_call","id":"sc_1","call_id":"call_3","status":"completed","action":{"commands":["ls"]}}"""

  val jsonOutputTextPart: String = """{"type":"output_text","text":"Hi","annotations":[]}"""
  val jsonRefusalPart: String = """{"type":"refusal","refusal":"I cannot help with that."}"""
  val jsonReasoningTextPart: String = """{"type":"reasoning_text","text":"Let me think."}"""
  private val jsonSummaryTextPart = """{"type":"summary_text","text":"Considered the request."}"""

  private val jsonLogprobs =
    """"logprobs":[{"token":"Hi","logprob":-0.01,"top_logprobs":[{"token":"Hi","logprob":-0.01}]}]"""

  val jsonAnnotationFileCitation: String = """{"type":"file_citation","file_id":"file_1","filename":"a.pdf","index":3}"""
  val jsonAnnotationUrlCitation: String =
    """{"type":"url_citation","end_index":5,"start_index":0,"title":"Example","url":"https://example.com"}"""
  val jsonAnnotationContainerFileCitation: String =
    """{"type":"container_file_citation","container_id":"c_1","end_index":5,"file_id":"file_2","filename":"b.txt","start_index":0}"""
  val jsonAnnotationFilePath: String = """{"type":"file_path","file_id":"file_3","index":1}"""

  private def lifecycle(t: String): String = frame(t, s""""response":${ResponsesFixture.jsonResponseBasic}""")
  private def itemProgress(t: String): String = frame(t, itemId, outputIndex)
  private def itemDelta(t: String): String = frame(t, itemId, outputIndex, """"delta":"x"""")

  // --- frames worth naming, because a spec asserts something specific about them ---

  val jsonCreated: String = lifecycle("response.created")
  val jsonCompleted: String = lifecycle("response.completed")

  /** `response.completed` whose usage reports every token-detail field, including `cache_write_tokens`. */
  val jsonCompletedWithFullUsage: String = {
    val usage =
      """"usage":{"input_tokens":328,"input_tokens_details":{"cached_tokens":64,"cache_write_tokens":16},""" +
        """"output_tokens":52,"output_tokens_details":{"reasoning_tokens":12},"total_tokens":380}"""
    val response = ResponsesFixture.jsonResponseBasic
    val withUsage = response.substring(0, response.lastIndexOf('}')).trim.stripSuffix(",") + "," + usage + "}"
    frame("response.completed", s""""response":$withUsage""")
  }

  val jsonOutputItemAddedFunctionCall: String = frame("response.output_item.added", s""""item":$jsonFunctionCallItem""", outputIndex)
  val jsonOutputItemAddedCustomToolCall: String = frame("response.output_item.added", s""""item":$jsonCustomToolCallItem""", outputIndex)
  val jsonOutputItemAddedUnknownItem: String = frame("response.output_item.added", s""""item":$jsonUnknownItem""", outputIndex)

  def jsonContentPartAdded(part: String): String =
    frame("response.content_part.added", contentIndex, itemId, outputIndex, s""""part":$part""")

  val jsonOutputTextDeltaWithLogprobs: String =
    frame("response.output_text.delta", contentIndex, """"delta":"Hi"""", itemId, outputIndex, jsonLogprobs)

  val jsonOutputTextDeltaNoLogprobs: String =
    frame("response.output_text.delta", contentIndex, """"delta":"Hi"""", itemId, outputIndex)

  /** A *known* event type with a missing required field: must still fail, so the `Unknown` fallback cannot mask modelling bugs. */
  val jsonOutputTextDeltaMissingItemId: String =
    frame("response.output_text.delta", contentIndex, """"delta":"Hi"""", outputIndex)

  def jsonAnnotationAdded(annotation: String): String =
    frame(
      "response.output_text.annotation.added",
      """"annotation_index":0""",
      contentIndex,
      itemId,
      outputIndex,
      s""""annotation":$annotation"""
    )

  val jsonAnnotationAddedNull: String = jsonAnnotationAdded("null")

  val jsonReasoningSummaryPartDoneIncomplete: String =
    frame(
      "response.reasoning_summary_part.done",
      itemId,
      outputIndex,
      s""""part":$jsonSummaryTextPart""",
      summaryIndex,
      """"status":"incomplete""""
    )

  val jsonShellCallCommandDeltaWithObfuscation: String =
    frame("response.shell_call_command.delta", commandIndex, """"delta":"ls"""", outputIndex, """"obfuscation":"abc"""")

  private def shellOutput(outcome: String) = s"""{"outcome":$outcome,"stdout":"ok","stderr":""}"""

  val jsonShellCallOutputContentDoneExit: String =
    frame(
      "response.shell_call_output_content.done",
      commandIndex,
      itemId,
      s""""output":[${shellOutput("""{"type":"exit","exit_code":0}""")}]""",
      outputIndex
    )

  val jsonShellCallOutputContentDoneTimeout: String =
    frame(
      "response.shell_call_output_content.done",
      commandIndex,
      itemId,
      s""""output":[${shellOutput("""{"type":"timeout"}""")}]""",
      outputIndex
    )

  val jsonImageGenerationCallPartialImage: String =
    frame(
      "response.image_generation_call.partial_image",
      itemId,
      outputIndex,
      """"partial_image_b64":"aGk="""",
      """"partial_image_index":0"""
    )

  val jsonError: String =
    frame("error", """"message":"Something went wrong."""", """"code":"server_error"""", """"param":null""")

  /** An event type this library does not model: must decode to `ResponsesStreamEvent.Unknown`. */
  val jsonUnknownEventType: String = frame("response.brand_new.delta", itemId, """"delta":"x"""")

  /** One minimal, valid frame per modelled event type. */
  val minimalByType: Map[String, String] = Map(
    "response.created" -> jsonCreated,
    "response.in_progress" -> lifecycle("response.in_progress"),
    "response.completed" -> jsonCompleted,
    "response.failed" -> lifecycle("response.failed"),
    "response.incomplete" -> lifecycle("response.incomplete"),
    "response.queued" -> lifecycle("response.queued"),
    "response.output_item.added" -> jsonOutputItemAddedFunctionCall,
    "response.output_item.done" -> frame("response.output_item.done", s""""item":$jsonFunctionCallItem""", outputIndex),
    "response.content_part.added" -> jsonContentPartAdded(jsonOutputTextPart),
    "response.content_part.done" -> frame(
      "response.content_part.done",
      contentIndex,
      itemId,
      outputIndex,
      s""""part":$jsonOutputTextPart"""
    ),
    "response.output_text.delta" -> jsonOutputTextDeltaWithLogprobs,
    "response.output_text.done" -> frame("response.output_text.done", contentIndex, itemId, outputIndex, """"text":"Hi"""", jsonLogprobs),
    "response.output_text.annotation.added" -> jsonAnnotationAdded(jsonAnnotationUrlCitation),
    "response.refusal.delta" -> frame("response.refusal.delta", contentIndex, """"delta":"No"""", itemId, outputIndex),
    "response.refusal.done" -> frame("response.refusal.done", contentIndex, itemId, outputIndex, """"refusal":"No""""),
    "response.reasoning_text.delta" -> frame("response.reasoning_text.delta", contentIndex, """"delta":"hm"""", itemId, outputIndex),
    "response.reasoning_text.done" -> frame("response.reasoning_text.done", contentIndex, itemId, outputIndex, """"text":"hm""""),
    "response.reasoning_summary_text.delta" ->
      frame("response.reasoning_summary_text.delta", """"delta":"sum"""", itemId, outputIndex, summaryIndex),
    "response.reasoning_summary_text.done" ->
      frame("response.reasoning_summary_text.done", itemId, outputIndex, summaryIndex, """"text":"sum""""),
    "response.reasoning_summary_part.added" ->
      frame("response.reasoning_summary_part.added", itemId, outputIndex, s""""part":$jsonSummaryTextPart""", summaryIndex),
    "response.reasoning_summary_part.done" -> jsonReasoningSummaryPartDoneIncomplete,
    "response.function_call_arguments.delta" -> itemDelta("response.function_call_arguments.delta"),
    "response.function_call_arguments.done" ->
      frame("response.function_call_arguments.done", """"arguments":"{}"""", itemId, """"name":"get_weather"""", outputIndex),
    "response.file_search_call.in_progress" -> itemProgress("response.file_search_call.in_progress"),
    "response.file_search_call.searching" -> itemProgress("response.file_search_call.searching"),
    "response.file_search_call.completed" -> itemProgress("response.file_search_call.completed"),
    "response.web_search_call.in_progress" -> itemProgress("response.web_search_call.in_progress"),
    "response.web_search_call.searching" -> itemProgress("response.web_search_call.searching"),
    "response.web_search_call.completed" -> itemProgress("response.web_search_call.completed"),
    "response.code_interpreter_call.in_progress" -> itemProgress("response.code_interpreter_call.in_progress"),
    "response.code_interpreter_call.interpreting" -> itemProgress("response.code_interpreter_call.interpreting"),
    "response.code_interpreter_call.completed" -> itemProgress("response.code_interpreter_call.completed"),
    "response.code_interpreter_call_code.delta" -> itemDelta("response.code_interpreter_call_code.delta"),
    "response.code_interpreter_call_code.done" ->
      frame("response.code_interpreter_call_code.done", """"code":"print(1)"""", itemId, outputIndex),
    "response.image_generation_call.in_progress" -> itemProgress("response.image_generation_call.in_progress"),
    "response.image_generation_call.generating" -> itemProgress("response.image_generation_call.generating"),
    "response.image_generation_call.completed" -> itemProgress("response.image_generation_call.completed"),
    "response.image_generation_call.partial_image" -> jsonImageGenerationCallPartialImage,
    "response.mcp_call.in_progress" -> itemProgress("response.mcp_call.in_progress"),
    "response.mcp_call.completed" -> itemProgress("response.mcp_call.completed"),
    "response.mcp_call.failed" -> itemProgress("response.mcp_call.failed"),
    "response.mcp_call_arguments.delta" -> itemDelta("response.mcp_call_arguments.delta"),
    "response.mcp_call_arguments.done" -> frame("response.mcp_call_arguments.done", """"arguments":"{}"""", itemId, outputIndex),
    "response.mcp_list_tools.in_progress" -> itemProgress("response.mcp_list_tools.in_progress"),
    "response.mcp_list_tools.completed" -> itemProgress("response.mcp_list_tools.completed"),
    "response.mcp_list_tools.failed" -> itemProgress("response.mcp_list_tools.failed"),
    "response.custom_tool_call_input.delta" -> itemDelta("response.custom_tool_call_input.delta"),
    "response.custom_tool_call_input.done" -> frame("response.custom_tool_call_input.done", """"input":"echo hi"""", itemId, outputIndex),
    "response.shell_call_command.added" -> frame("response.shell_call_command.added", """"command":"ls"""", commandIndex, outputIndex),
    "response.shell_call_command.delta" -> jsonShellCallCommandDeltaWithObfuscation,
    "response.shell_call_command.done" -> frame("response.shell_call_command.done", """"command":"ls"""", commandIndex, outputIndex),
    "response.shell_call_output_content.delta" ->
      frame("response.shell_call_output_content.delta", commandIndex, """"delta":{"stdout":"ok"}""", itemId, outputIndex),
    "response.shell_call_output_content.done" -> jsonShellCallOutputContentDoneExit,
    "response.audio.delta" -> frame("response.audio.delta", """"delta":"aGk=""""),
    "response.audio.done" -> frame("response.audio.done"),
    "response.audio.transcript.delta" -> frame("response.audio.transcript.delta", """"delta":"Hi""""),
    "response.audio.transcript.done" -> frame("response.audio.transcript.done"),
    "error" -> jsonError
  )

  /** A realistic run of events, in stream order, for the streaming-module specs to push through their SSE plumbing. */
  val sseSequence: List[String] = List(
    jsonCreated,
    jsonOutputItemAddedFunctionCall,
    jsonContentPartAdded(jsonOutputTextPart),
    jsonOutputTextDeltaNoLogprobs,
    jsonOutputTextDeltaNoLogprobs,
    frame("response.output_text.done", contentIndex, itemId, outputIndex, """"text":"HiHi""""),
    frame("response.content_part.done", contentIndex, itemId, outputIndex, s""""part":$jsonOutputTextPart"""),
    frame("response.output_item.done", s""""item":$jsonFunctionCallItem""", outputIndex),
    jsonCompleted
  )
}
