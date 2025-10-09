package sttp.openai.requests.threads

import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.chat.message.ToolResources
import sttp.openai.requests.threads.messages.ThreadMessagesRequestBody.CreateMessage

object ThreadsRequestBody {

  /** @param messages
    *   A list of messages to start the thread with.
    * @param metadata
    *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object
    *   in a structured format. Keys can be a maximum of 64 characters long and values can be a maxium of 512 characters long.
    *
    * For more information please visit: [[https://platform.openai.com/docs/api-reference/threads/createThread]]
    */
  case class CreateThreadBody(
      messages: Option[Seq[CreateMessage]] = None,
      toolResources: Option[ToolResources] = None,
      metadata: Option[Map[String, String]] = None
  )

  object CreateThreadBody {
    implicit val completionBodyW: SnakePickle.Writer[CreateThreadBody] = SnakePickle.macroW[CreateThreadBody]
  }

}
