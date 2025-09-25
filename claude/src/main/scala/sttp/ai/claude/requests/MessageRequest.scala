package sttp.ai.claude.requests

import sttp.ai.claude.models.{Message, Tool}
import sttp.ai.claude.json.SnakePickle.{macroRW, ReadWriter}

case class MessageRequest(
    model: String,
    messages: List[Message],
    system: Option[String] = None,
    maxTokens: Int,
    temperature: Option[Double] = None,
    topP: Option[Double] = None,
    topK: Option[Int] = None,
    stopSequences: Option[List[String]] = None,
    stream: Option[Boolean] = None,
    tools: Option[List[Tool]] = None
)

object MessageRequest {
  def simple(
      model: String,
      messages: List[Message],
      maxTokens: Int
  ): MessageRequest = MessageRequest(
    model = model,
    messages = messages,
    maxTokens = maxTokens
  )

  def withSystem(
      model: String,
      system: String,
      messages: List[Message],
      maxTokens: Int
  ): MessageRequest = MessageRequest(
    model = model,
    messages = messages,
    system = Some(system),
    maxTokens = maxTokens
  )

  def withTools(
      model: String,
      messages: List[Message],
      maxTokens: Int,
      tools: List[Tool]
  ): MessageRequest = MessageRequest(
    model = model,
    messages = messages,
    maxTokens = maxTokens,
    tools = Some(tools)
  )

  implicit val rw: ReadWriter[MessageRequest] = macroRW
}
