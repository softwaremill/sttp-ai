package sttp.ai.openai.requests.completions.chat.message

import sttp.ai.openai.requests.assistants

case class Attachment(fileId: Option[String] = None, tools: Option[Seq[assistants.Tool]] = None)
