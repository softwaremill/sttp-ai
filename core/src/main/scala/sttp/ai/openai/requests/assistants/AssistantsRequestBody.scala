package sttp.openai.requests.assistants

import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.chat.message.ToolResources

object AssistantsRequestBody {

  /** @param model
    *   ID of the model to use. You can use the List models API to see all of your available models, or see our Model overview for
    *   descriptions of them.
    * @param name
    *   The name of the assistant. The maximum length is 256 characters.
    * @param description
    *   The description of the assistant. The maximum length is 512 characters.
    * @param instructions
    *   The system instructions that the assistant uses. The maximum length is 32768 characters.
    * @param reasoningEffort
    *   o1 and o3-mini models only Constrains effort on reasoning for reasoning models. Currently supported values are low, medium, and
    *   high. Reducing reasoning effort can result in faster responses and fewer tokens used on reasoning in a response.
    * @param tools
    *   A list of tool enabled on the assistant. There can be a maximum of 128 tools per assistant. Tools can be of types code_interpreter,
    *   file_search, or function.
    * @param toolResources
    *   A set of resources that are used by the assistant's tools. The resources are specific to the type of tool. For example, the
    *   code_interpreter tool requires a list of file IDs, while the file_search tool requires a list of vector store IDs.
    * @param metadata
    *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object
    *   in a structured format. Keys can be a maximum of 64 characters long and values can be a maxium of 512 characters long.
    * @param temperature
    *   What sampling temperature to use, between 0 and 2. Higher values like 0.8 will make the output more random, while lower values like
    *   0.2 will make it more focused and deterministic.
    * @param topP
    *   An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens with top_p
    *   probability mass. So 0.1 means only the tokens comprising the top 10% probability mass are considered. We generally recommend
    *   altering this or temperature but not both.
    *
    * For more information please visit: [[https://platform.openai.com/docs/api-reference/assistants/createAssistant]]
    */
  case class CreateAssistantBody(
      model: AssistantsModel,
      name: Option[String] = None,
      description: Option[String] = None,
      instructions: Option[String] = None,
      reasoningEffort: Option[ReasoningEffort] = None,
      tools: Seq[Tool] = Seq.empty,
      toolResources: Option[ToolResources] = None,
      metadata: Option[Map[String, String]] = None,
      temperature: Option[Float] = None,
      topP: Option[Float] = None
  )
  object CreateAssistantBody {
    implicit val createAssistantBodyW: SnakePickle.Writer[CreateAssistantBody] = SnakePickle.macroW[CreateAssistantBody]
  }

  /** @param model
    *   ID of the model to use. You can use the List models API to see all of your available models, or see our Model overview for
    *   descriptions of them.
    * @param name
    *   The name of the assistant. The maximum length is 256 characters.
    * @param description
    *   The description of the assistant. The maximum length is 512 characters.
    * @param instructions
    *   The system instructions that the assistant uses. The maximum length is 32768 characters.
    * @param reasoningEffort
    *   o1 and o3-mini models only
    *
    * Constrains effort on reasoning for reasoning models. Currently supported values are low, medium, and high. Reducing reasoning effort
    * can result in faster responses and fewer tokens used on reasoning in a response.
    * @param tools
    *   A list of tool enabled on the assistant. There can be a maximum of 128 tools per assistant. Tools can be of types code_interpreter,
    *   file_search, or function.
    * @param toolResources
    *   A set of resources that are used by the assistant's tools. The resources are specific to the type of tool. For example, the
    *   code_interpreter tool requires a list of file IDs, while the file_search tool requires a list of vector store IDs. v
    * @param metadata
    *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object
    *   in a structured format. Keys can be a maximum of 64 characters long and values can be a maxium of 512 characters long.
    * @param temperature
    *   What sampling temperature to use, between 0 and 2. Higher values like 0.8 will make the output more random, while lower values like
    *   0.2 will make it more focused and deterministic.
    * @param topP
    *   An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens with top_p
    *   probability mass. So 0.1 means only the tokens comprising the top 10% probability mass are considered. We generally recommend
    *   altering this or temperature but not both.
    *
    * For more information please visit: [[https://platform.openai.com/docs/api-reference/assistants/modifyAssistant]]
    */
  case class ModifyAssistantBody(
      model: Option[String] = None,
      name: Option[String] = None,
      description: Option[String] = None,
      instructions: Option[String] = None,
      reasoningEffort: Option[ReasoningEffort] = None,
      tools: Seq[Tool] = Seq.empty,
      toolResources: Option[ToolResources] = None,
      metadata: Map[String, String] = Map.empty,
      temperature: Option[Float] = None,
      topP: Option[Float] = None
  )

  object ModifyAssistantBody {
    implicit val modifyAssistantBodyW: SnakePickle.Writer[ModifyAssistantBody] = SnakePickle.macroW[ModifyAssistantBody]
  }
}

sealed abstract class ReasoningEffort(val value: String)

object ReasoningEffort {

  implicit val reasoningEffortW: SnakePickle.Writer[ReasoningEffort] = SnakePickle
    .writer[ujson.Value]
    .comap[ReasoningEffort](reasoningEffort => SnakePickle.writeJs(reasoningEffort.value))

  case object Low extends ReasoningEffort("low")

  case object Medium extends ReasoningEffort("medium")

  case object High extends ReasoningEffort("high")

  case class CustomReasoningEffort(customReasoningEffort: String) extends ReasoningEffort(customReasoningEffort)

}
