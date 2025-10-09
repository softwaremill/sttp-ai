package sttp.openai.requests.threads.runs

import sttp.openai.json.SnakePickle
import sttp.openai.requests.assistants.Tool
import sttp.openai.requests.completions.chat.message.ToolResources
import sttp.openai.requests.threads.runs.ThreadRunsResponseData.FileSearchToolCall.FileSearch
import sttp.openai.requests.threads.runs.ThreadRunsResponseData.FileSearchToolCall.FileSearch.FileSearchResult.Content
import sttp.openai.requests.threads.runs.ThreadRunsResponseData.FileSearchToolCall.FileSearch.{FileSearchResult, RankingOptions}

object ThreadRunsResponseData {

  /** Represents an execution run on a thread
    *
    * @param id
    *   The identifier, which can be referenced in API endpoints.
    *
    * @param object
    *   The object type, which is always thread.run.
    *
    * @param createdAt
    *   The Unix timestamp (in seconds) for when the run was created.
    *
    * @param threadId
    *   The ID of the thread that was executed on as a part of this run.
    *
    * @param assistantId
    *   The ID of the assistant used for execution of this run.
    *
    * @param status
    *   The status of the run, which can be either queued, in_progress, requires_action, cancelling, cancelled, failed, completed, or
    *   expired.
    *
    * @param requiredAction
    *   Details on the action required to continue the run. Will be null if no action is required.
    *
    * @param lastError
    *   The last error associated with this run. Will be null if there are no errors.
    *
    * @param expiresAt
    *   The Unix timestamp (in seconds) for when the run will expire.
    *
    * @param startedAt
    *   The Unix timestamp (in seconds) for when the run was started.
    *
    * @param cancelledAt
    *   The Unix timestamp (in seconds) for when the run was cancelled.
    *
    * @param failedAt
    *   The Unix timestamp (in seconds) for when the run failed.
    *
    * @param completedAt
    *   The Unix timestamp (in seconds) for when the run was completed.
    *
    * @param model
    *   The model that the assistant used for this run.
    *
    * @param instructions
    *   The instructions that the assistant used for this run.
    *
    * @param tools
    *   The list of tools that the assistant used for this run.
    *
    * @param toolResources
    *   A set of resources that are used by the assistant's tools. The resources are specific to the type of tool. For example, the
    *   code_interpreter tool requires a list of file IDs, while the file_search tool requires a list of vector store IDs.
    *
    * @param metadata
    *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object
    *   in a structured format. Keys can be a maximum of 64 characters long and values can be a maxium of 512 characters long.
    *
    * @param usage
    *   Usage statistics related to the run. This value will be null if the run is not in a terminal state (i.e. in_progress, queued, etc.).
    *
    * For more information please visit: [[https://platform.openai.com/docs/api-reference/runs/object]]
    */
  case class RunData(
      id: String,
      `object`: String,
      createdAt: Int,
      threadId: String,
      assistantId: String,
      status: String,
      requiredAction: Option[RequiredAction] = None,
      lastError: Option[Error] = None,
      expiresAt: Option[Int] = None,
      startedAt: Option[Int] = None,
      cancelledAt: Option[Int] = None,
      failedAt: Option[Int] = None,
      completedAt: Option[Int] = None,
      model: String,
      instructions: Option[String] = None,
      tools: Seq[Tool] = Seq.empty,
      toolResources: Option[ToolResources] = None,
      metadata: Map[String, String] = Map.empty,
      usage: Option[Usage] = None
  )

  object RunData {
    implicit val RunDataR: SnakePickle.Reader[RunData] = SnakePickle.macroR[RunData]
  }

  /** Details on the action required to continue the run. Will be null if no action is required
    *
    * [[https://platform.openai.com/docs/api-reference/runs/object]]
    */
  trait RequiredAction

  implicit val requiredActionR: SnakePickle.Reader[RequiredAction] = SnakePickle
    .reader[ujson.Value]
    .map(json =>
      json("type").str match {
        case "submit_tool_outputs" =>
          SnakePickle.read[SubmitToolOutputsRequiredAction](json)
      }
    )

  /** @param type
    *   For now, this is always submit_tool_outputs.
    *
    * @param submitToolOutputs
    *   Details on the tool outputs needed for this run to continue.
    */
  case class SubmitToolOutputsRequiredAction(
      `type`: String,
      submitToolOutputs: SubmitToolOutputs
  ) extends RequiredAction

  object SubmitToolOutputsRequiredAction {
    implicit val submitToolOutputsRequiredActionR: SnakePickle.Reader[SubmitToolOutputsRequiredAction] =
      SnakePickle.macroR[SubmitToolOutputsRequiredAction]
  }

  /** @param toolCalls
    *   A list of the relevant tool calls.
    */
  case class SubmitToolOutputs(toolCalls: Seq[ToolCall])

  object SubmitToolOutputs {
    implicit val submitToolOutputsR: SnakePickle.Reader[SubmitToolOutputs] = SnakePickle.macroR[SubmitToolOutputs]
  }

  /** The last error associated with this run
    */
  trait Error {

    /** One of server_error, rate_limit_exceeded, or invalid_prompt.
      */
    def code: String

    /** A human-readable description of the error.
      */
    def message: String
  }

  object Error {
    implicit val errorR: SnakePickle.Reader[Error] = SnakePickle
      .reader[ujson.Value]
      .map(json =>
        json("code").str match {
          case "server_error" =>
            SnakePickle.read[ServerError](json)
          case "rate_limit_exceeded" =>
            SnakePickle.read[RateLimitExceeded](json)
          case "invalid_prompt" =>
            SnakePickle.read[InvalidPrompt](json)
        }
      )

    /** @param code
      *   server_error
      * @param message
      *   A human-readable description of the error.
      */
    case class ServerError(code: String, message: String) extends Error

    implicit val serverErrorR: SnakePickle.Reader[ServerError] = SnakePickle.macroR[ServerError]

    /** @param code
      *   rate_limit_exceeded
      * @param message
      *   A human-readable description of the error.
      */
    case class RateLimitExceeded(code: String, message: String) extends Error

    implicit val rateLimitExceededR: SnakePickle.Reader[RateLimitExceeded] = SnakePickle.macroR[RateLimitExceeded]

    /** @param code
      *   invalid_prompt
      * @param message
      *   A human-readable description of the error.
      */
    case class InvalidPrompt(code: String, message: String) extends Error

    implicit val invalidPromptR: SnakePickle.Reader[InvalidPrompt] = SnakePickle.macroR[InvalidPrompt]
  }

  /** @param object
    *   Always "list"
    * @param data
    *   A list of run objects.
    * @param firstId
    * @param lastId
    * @param hasMore
    *   }
    */
  case class ListRunsResponse(
      `object`: String = "list",
      data: Seq[RunData],
      firstId: String,
      lastId: String,
      hasMore: Boolean
  )

  object ListRunsResponse {
    implicit val listRunsResponseR: SnakePickle.Reader[ListRunsResponse] = SnakePickle.macroR[ListRunsResponse]
  }

  /** Represents a step in execution of a run
    *
    * @param id
    *   The identifier of the run step, which can be referenced in API endpoints.
    *
    * @param object
    *   The object type, which is always thread.run.step.
    *
    * @param createdAt
    *   The Unix timestamp (in seconds) for when the run step was created.
    *
    * @param assistantId
    *   The ID of the assistant associated with the run step.
    *
    * @param threadId
    *   The ID of the thread that was run.
    *
    * @param runId
    *   The ID of the run that this run step is a part of.
    *
    * @param type
    *   The type of run step, which can be either message_creation or tool_calls.
    *
    * @param status
    *   The status of the run step, which can be either in_progress, cancelled, failed, completed, or expired.
    *
    * @param stepDetails
    *   The details of the run step.
    *
    * @param lastError
    *   The last error associated with this run step. Will be null if there are no errors.
    *
    * @param expiredAt
    *   The Unix timestamp (in seconds) for when the run step expired. A step is considered expired if the parent run is expired.
    *
    * @param cancelledAt
    *   The Unix timestamp (in seconds) for when the run step was cancelled.
    *
    * @param failedAt
    *   The Unix timestamp (in seconds) for when the run step failed.
    *
    * @param completedAt
    *   The Unix timestamp (in seconds) for when the run step completed.
    *
    * @param metadata
    *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object
    *   in a structured format. Keys can be a maximum of 64 characters long and values can be a maxium of 512 characters long.
    *
    * @param usage
    *   Usage statistics related to the run step. This value will be null while the run step's status is in_progress.
    */

  case class RunStepData(
      id: String,
      `object`: String,
      createdAt: Int,
      assistantId: String,
      threadId: String,
      runId: String,
      `type`: String,
      status: String,
      stepDetails: StepDetails,
      lastError: Option[Error] = None,
      expiredAt: Option[Int] = None,
      cancelledAt: Option[Int] = None,
      failedAt: Option[Int] = None,
      completedAt: Option[Int] = None,
      metadata: Map[String, String] = Map.empty,
      usage: Option[Usage] = None
  )

  object RunStepData {
    implicit val runStepDataR: SnakePickle.Reader[RunStepData] = SnakePickle.macroR[RunStepData]
  }

  /** The details of the run step.
    */
  trait StepDetails

  object StepDetails {
    implicit val stepDetailsR: SnakePickle.Reader[StepDetails] = SnakePickle
      .reader[ujson.Value]
      .map(json =>
        json("type").str match {
          case "message_creation" =>
            SnakePickle.read[MessageCreation](json("message_creation"))
          case "tool_calls" =>
            SnakePickle.read[ToolCalls](json)
        }
      )
  }

  /** Details of the message creation by the run step
    *
    * @param messageId
    *   The ID of the message that was created by this run step.
    */
  case class MessageCreation(messageId: String) extends StepDetails

  object MessageCreation {
    implicit val messageCreationR: SnakePickle.Reader[MessageCreation] = SnakePickle.macroR[MessageCreation]
  }

  /** Details of the tool call
    * @param `type`
    *   Always tool_calls.
    * @param toolCalls
    *   An array of tool calls the run step was involved in. These can be associated with one of three types of tools: code_interpreter,
    *   file_search, or function.
    */
  case class ToolCalls(`type`: String, toolCalls: Seq[ToolCall]) extends StepDetails

  object ToolCalls {
    implicit val toolCallsR: SnakePickle.Reader[ToolCalls] = SnakePickle.macroR[ToolCalls]
  }

  /** Details of the tool call
    */

  trait ToolCall

  object ToolCall {
    implicit val toolCallsR: SnakePickle.Reader[ToolCall] = SnakePickle
      .reader[ujson.Value]
      .map(json =>
        json("type").str match {
          case "code_interpreter" =>
            SnakePickle.read[CodeInterpreterToolCall](json)
          case "file_search" =>
            SnakePickle.read[FileSearchToolCall](json)
          case "function" =>
            SnakePickle.read[FunctionToolCall](json)
        }
      )
  }

  /** Details of the Code Interpreter tool call the run step was involved in.
    * @param id
    *   The ID of the tool call.
    *
    * @param type
    *   The type of tool call. This is always going to be code_interpreter for this type of tool call.
    *
    * @param codeInterpreter
    *   The Code Interpreter tool call definition.
    */
  case class CodeInterpreterToolCall(
      id: String,
      `type`: String
      // TODO codeInterpreter
  ) extends ToolCall

  object CodeInterpreterToolCall {
    implicit val codeInterpreterToolCallR: SnakePickle.Reader[CodeInterpreterToolCall] = SnakePickle.macroR[CodeInterpreterToolCall]
  }

  /** FileSearch tool call
    * @param id
    *   The ID of the tool call object.
    *
    * @param type
    *   The type of tool call. This is always going to be file_search for this type of tool call.
    *
    * @param fileSearch
    *   According to https://platform.openai.com/docs/api-reference/run-steps/step-object It should be map: "For now, this is always going
    *   to be an empty object."
    *
    * Actually, it has two fields: "ranking_options" and "results"
    */
  case class FileSearchToolCall(
      id: String,
      `type`: String,
      fileSearch: Option[FileSearch] = None
  ) extends ToolCall

  object FileSearchToolCall {
    implicit val fileSearchToolCallR: SnakePickle.Reader[FileSearchToolCall] = SnakePickle.macroR[FileSearchToolCall]

    /** @param rankingOptions
      *   The ranking options for the file search
      * @param results
      *   The results of the file search
      */
    case class FileSearch(
        rankingOptions: Option[RankingOptions] = None,
        results: Seq[FileSearchResult] = Seq.empty
    )

    object FileSearch {
      implicit val fileSearchR: SnakePickle.Reader[FileSearch] = SnakePickle.macroR[FileSearch]

      /** The ranking options for the file search
        *
        * @param ranker
        *   The ranker to use for the file search. If not specified will use the auto ranker.
        * @param scoreThreshold
        *   The score threshold for the file search. All values must be a floating point number between 0 and 1
        */
      case class RankingOptions(
          ranker: String,
          scoreThreshold: Double
      )

      object RankingOptions {
        implicit val rankingOptionsR: SnakePickle.Reader[RankingOptions] = SnakePickle.macroR[RankingOptions]
      }

      /** The results of the file search
        *
        * @param fileId
        *   The ID of the file that result was found in
        * @param fileName
        *   The name of the file that result was found in
        * @param score
        *   The score of the result. All values must be a floating point number between 0 and 1.
        * @param content
        *   The content of the result that was found. The content is only included if requested via the include query parameter.
        */
      case class FileSearchResult(
          fileId: String,
          fileName: String,
          score: Double,
          content: Seq[Content] = Seq.empty
      )

      object FileSearchResult {
        implicit val fileSearchResultR: SnakePickle.Reader[FileSearchResult] = SnakePickle.macroR[FileSearchResult]

        /** The content of the result that was found.
          *
          * @param text
          *   The text content of the file.
          * @param type
          *   The type of the content.
          */
        case class Content(
            text: String,
            `type`: String
        )

        object Content {
          implicit val contentR: SnakePickle.Reader[Content] = SnakePickle.macroR[Content]
        }

      }

    }
  }

  /** Function tool call
    *
    * @param id
    *   The ID of the tool call object.
    * @param type
    *   The type of tool call. This is always going to be function for this type of tool call.
    * @param function
    *   The definition of the function that was called.
    */
  case class FunctionToolCall(
      id: String,
      `type`: String,
      function: FunctionCallResult
  ) extends ToolCall

  object FunctionToolCall {
    implicit val functionToolCallR: SnakePickle.Reader[FunctionToolCall] = SnakePickle.macroR[FunctionToolCall]
  }

  /** The definition of the function that was called
    *
    * @param name
    *   The name of the function.
    *
    * @param arguments
    *   The arguments passed to the function.
    *
    * @param output
    *   The output of the function. This will be null if the outputs have not been submitted yet.
    */
  case class FunctionCallResult(name: String, arguments: String, output: Option[String])
  object FunctionCallResult {
    implicit val functionCallResultR: SnakePickle.Reader[FunctionCallResult] = SnakePickle.macroR[FunctionCallResult]
  }

  /** @param object
    *   Always "list"
    * @param data
    *   A list of run objects.
    * @param firstId
    * @param lastId
    * @param hasMore
    *   }
    */
  case class ListRunStepsResponse(
      `object`: String = "list",
      data: Seq[RunStepData],
      firstId: String,
      lastId: String,
      hasMore: Boolean
  )
  object ListRunStepsResponse {
    implicit val listRunStepsResponseR: SnakePickle.Reader[ListRunStepsResponse] = SnakePickle.macroR[ListRunStepsResponse]
  }

  /** @param promptTokens
    *   Number of tokens in the prompt.
    * @param completionTokens
    *   Number of tokens in the generated completion.
    * @param totalTokens
    *   Total number of tokens used in the request (prompt + completion).
    */
  case class Usage(promptTokens: Int, completionTokens: Int, totalTokens: Int)

  object Usage {
    implicit val choicesR: SnakePickle.Reader[Usage] = SnakePickle.macroR[Usage]
  }
}
