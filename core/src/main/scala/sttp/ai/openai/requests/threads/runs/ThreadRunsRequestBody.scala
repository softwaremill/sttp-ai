package sttp.openai.requests.threads.runs

import sttp.openai.json.SnakePickle
import sttp.openai.requests.assistants.Tool
import sttp.openai.requests.completions.chat.message.ToolResources
import sttp.openai.requests.threads.ThreadsRequestBody.CreateThreadBody

object ThreadRunsRequestBody {

  /** @param assistantId
    *   The ID of the assistant to use to execute this run.
    *
    * @param model
    *   The ID of the Model to be used to execute this run. If a value is provided here, it will override the model associated with the
    *   assistant. If not, the model associated with the assistant will be used.
    *
    * @param instructions
    *   Overrides the instructions of the assistant. This is useful for modifying the behavior on a per-run basis.
    *
    * @param additionalInstructions
    *   Appends additional instructions at the end of the instructions for the run. This is useful for modifying the behavior on a per-run
    *   basis without overriding other instructions.
    *
    * @param tools
    *   Override the tools the assistant can use for this run. This is useful for modifying the behavior on a per-run basis.
    *
    * @param metadata
    *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object
    *   in a structured format. Keys can be a maximum of 64 characters long and values can be a maxium of 512 characters long.
    *
    * For more information please visit: [[https://platform.openai.com/docs/api-reference/runs/createRun]]
    */
  case class CreateRun(
      assistantId: String,
      model: Option[String] = None,
      instructions: Option[String] = None,
      additionalInstructions: Option[String] = None,
      tools: Seq[Tool] = Seq.empty,
      metadata: Map[String, String] = Map.empty
  )

  object CreateRun {
    implicit val createRunW: SnakePickle.Writer[CreateRun] = SnakePickle.macroW[CreateRun]
  }

  /** @param assistantId
    *   The ID of the assistant to use to execute this run.
    *
    * @param thread
    *
    * @param model
    *   The ID of the Model to be used to execute this run. If a value is provided here, it will override the model associated with the
    *   assistant. If not, the model associated with the assistant will be used.
    *
    * @param instructions
    *   Overrides the instructions of the assistant. This is useful for modifying the behavior on a per-run basis.
    *
    * @param tools
    *   Override the tools the assistant can use for this run. This is useful for modifying the behavior on a per-run basis.
    *
    * @param metadata
    *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object
    *   in a structured format. Keys can be a maximum of 64 characters long and values can be a maxium of 512 characters long.
    *
    * For more information please visit: [[https://platform.openai.com/docs/api-reference/runs/createThreadAndRun]]
    */
  case class CreateThreadAndRun(
      assistantId: String,
      thread: CreateThreadBody,
      model: Option[String] = None,
      instructions: Option[String] = None,
      tools: Seq[Tool] = Seq.empty,
      toolResources: Option[ToolResources] = None,
      metadata: Map[String, String] = Map.empty
  )

  object CreateThreadAndRun {
    implicit val createThreadAndRunW: SnakePickle.Writer[CreateThreadAndRun] = SnakePickle.macroW[CreateThreadAndRun]
  }

  /** @param metadata
    *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object
    *   in a structured format. Keys can be a maximum of 64 characters long and values can be a maxium of 512 characters long.
    *
    * For more information please visit: [[https://platform.openai.com/docs/api-reference/runs/modifyRun]]
    */
  case class ModifyRun(
      metadata: Map[String, String] = Map.empty
  )

  object ModifyRun {
    implicit val modifyRunW: SnakePickle.Writer[ModifyRun] = SnakePickle.macroW[ModifyRun]
  }

  /** @param toolOutputs
    *   A list of tools for which the outputs are being submitted.
    *
    * For more information please visit: [[https://platform.openai.com/docs/api-reference/runs/submitToolOutputs]]
    */
  case class SubmitToolOutputsToRun(
      toolOutputs: Seq[ToolOutput]
  )
  object SubmitToolOutputsToRun {
    implicit val submitToolOutputsToRunW: SnakePickle.Writer[SubmitToolOutputsToRun] = SnakePickle.macroW[SubmitToolOutputsToRun]
  }

  /** @param toolCallId
    *   The ID of the tool call in the required_action object within the run object the output is being submitted for.
    *
    * @param output
    *   The output of the tool call to be submitted to continue the run.
    */
  case class ToolOutput(toolCallId: Option[String], output: String)

  object ToolOutput {
    implicit val toolOutputW: SnakePickle.Writer[ToolOutput] = SnakePickle.macroW[ToolOutput]
  }
}
