package sttp.openai.requests.assistants

import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.chat.message.ToolResources

object AssistantsResponseData {

  /** Represents an assistant that can call the model and use tools.
    * @param id
    *   The identifier, which can be referenced in API endpoints.
    *
    * @param object
    *   The object type, which is always assistant.
    *
    * @param createdAt
    *   The Unix timestamp (in seconds) for when the assistant was created.
    *
    * @param name
    *   The name of the assistant. The maximum length is 256 characters.
    *
    * @param description
    *   The description of the assistant. The maximum length is 512 characters.
    *
    * @param model
    *   ID of the model to use. You can use the List models API to see all of your available models, or see our Model overview for
    *   descriptions of them.
    *
    * @param instructions
    *   The system instructions that the assistant uses. The maximum length is 32768 characters.
    *
    * @param tools
    *   A list of tool enabled on the assistant. There can be a maximum of 128 tools per assistant. Tools can be of types code_interpreter,
    *   file_search, or function.
    *
    * @param toolResources
    *   A set of resources that are used by the assistant's tools. The resources are specific to the type of tool. For example, the
    *   code_interpreter tool requires a list of file IDs, while the file_search tool requires a list of vector store IDs.
    *
    * @param metadata
    *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object
    *   in a structured format. Keys can be a maximum of 64 characters long and values can be a maxium of 512 characters long.
    *
    * For more information please visit: [[https://platform.openai.com/docs/api-reference/assistants/object]]
    */
  case class AssistantData(
      id: String,
      `object`: String,
      createdAt: Int,
      name: Option[String],
      description: Option[String],
      model: AssistantsModel,
      instructions: Option[String],
      tools: Seq[Tool],
      toolResources: Option[ToolResources],
      metadata: Map[String, String]
  )

  object AssistantData {
    implicit val assistantDataR: SnakePickle.Reader[AssistantData] = SnakePickle.macroR[AssistantData]
  }

  /** @param object
    *   Always "list"
    * @param data
    *   A list of assistant objects.
    * @param firstId
    *   Id of first object
    * @param lastId
    *   Id of last object
    * @param hasMore
    *   Denotes if there are more object available }
    */
  case class ListAssistantsResponse(
      `object`: String = "list",
      data: Seq[AssistantData],
      firstId: String,
      lastId: String,
      hasMore: Boolean
  )
  object ListAssistantsResponse {
    implicit val listAssistantsResponseR: SnakePickle.Reader[ListAssistantsResponse] = SnakePickle.macroR[ListAssistantsResponse]
  }

  /** @param id
    *   Id of deleted object
    * @param `object`
    *   assistant.deleted
    * @param deleted
    *
    * For more information please visit: [[https://platform.openai.com/docs/api-reference/assistants/deleteAssistant]]
    */
  case class DeleteAssistantResponse(
      id: String,
      `object`: String,
      deleted: Boolean
  )

  object DeleteAssistantResponse {
    implicit val deleteAssistantResponseReadWriter: SnakePickle.ReadWriter[DeleteAssistantResponse] =
      SnakePickle.macroRW[DeleteAssistantResponse]
  }
}
