package sttp.ai.openai.requests.responses

import sttp.ai.openai.requests.responses.ResponsesRequestBody.Input
import sttp.ai.openai.requests.responses.ResponsesResponseBody.{OutputContent, OutputItem, Usage}

/** A compacted conversation, returned by standalone compaction.
  *
  * @param id
  *   The unique identifier for the compacted response.
  * @param createdAt
  *   Unix timestamp (in seconds) of when the conversation was compacted.
  * @param `object`
  *   Always `response.compaction`.
  * @param output
  *   The compacted conversation: the retained user messages, followed by a single [[OutputItem.Compaction]] item.
  * @param usage
  *   Token usage of the compaction.
  */
case class CompactedResponse(
    id: String,
    createdAt: Long,
    `object`: String,
    output: List[OutputItem],
    usage: Usage
) {

  /** The compacted conversation as input items, to start a later request from. Carries the compaction items and the text of the retained
    * user messages; other items, non-text message parts and user messages without text are dropped.
    */
  def toInput: List[Input] = output.flatMap {
    case c: OutputItem.Compaction                  => List(c.toInput)
    case OutputItem.Message(content, _, "user", _) =>
      // output content models only assistant parts, so the user's `input_text` parts decode as `Unknown`
      val texts = content.flatMap {
        case OutputContent.Unknown("input_text", raw) => raw.hcursor.get[String]("text").toOption
        case _                                        => None
      }
      if (texts.isEmpty) Nil else List(Input.InputMessage(texts.map(Input.InputContentItem.InputText(_)), role = "user", status = None))
    case _ => Nil
  }
}
