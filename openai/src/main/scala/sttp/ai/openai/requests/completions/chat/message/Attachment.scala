package sttp.ai.openai.requests.completions.chat.message

import sttp.ai.openai.requests.assistants.Tool

case class Attachment(fileId: Option[String] = None, tools: Option[Seq[Tool]] = None)
