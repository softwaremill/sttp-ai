package sttp.ai.claude.requests

import sttp.ai.claude.models.{Effort, Message, OutputConfig, OutputFormat, Tool}
import sttp.ai.core.json.SnakePickle.{macroRW, ReadWriter}

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
    tools: Option[List[Tool]] = None,
    outputConfig: Option[OutputConfig] = None
) {
  def usesStructuredOutput: Boolean = outputConfig.exists(_.format.exists(_.isInstanceOf[OutputFormat.JsonSchema]))

  def withStructuredOutput(format: OutputFormat): MessageRequest = {
    val updated = outputConfig.getOrElse(OutputConfig()).copy(format = Some(format))
    this.copy(outputConfig = Some(updated))
  }

  def withEffort(effort: Effort): MessageRequest = {
    val updated = outputConfig.getOrElse(OutputConfig()).copy(effort = Some(effort))
    this.copy(outputConfig = Some(updated))
  }
}

object MessageRequest {
  def simple(
      model: String,
      messages: List[Message],
      maxTokens: Int,
      outputConfig: Option[OutputConfig] = None
  ): MessageRequest = MessageRequest(
    model = model,
    messages = messages,
    maxTokens = maxTokens,
    outputConfig = outputConfig
  )

  def withSystem(
      model: String,
      system: String,
      messages: List[Message],
      maxTokens: Int,
      outputConfig: Option[OutputConfig] = None
  ): MessageRequest = MessageRequest(
    model = model,
    messages = messages,
    system = Some(system),
    maxTokens = maxTokens,
    outputConfig = outputConfig
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
