package sttp.ai.openai.requests.completions.chat.message

import sttp.ai.openai.requests.completions.chat.message.ToolResource.{CodeInterpreterToolResource, FileSearchToolResource}

case class ToolResources(
    codeInterpreter: Option[CodeInterpreterToolResource] = None,
    fileSearch: Option[FileSearchToolResource] = None
)
