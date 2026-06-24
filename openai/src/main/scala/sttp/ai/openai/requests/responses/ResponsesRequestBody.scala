package sttp.ai.openai.requests.responses

import io.circe.Json
import sttp.apispec.Schema
import sttp.ai.openai.requests.caching.CacheRetentionPolicy
import sttp.ai.openai.requests.responses.ResponsesRequestBody.Input
import sttp.ai.openai.requests.responses.ResponsesRequestBody.Input.OutputContentItem.OutputText.{Annotation, LogProb}
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TSchema}

/** @param background
  *   Whether to run the model response in the background. Defaults to false.
  * @param include
  *   Specify additional output data to include in the model response. Currently supported values are:
  *   - `code_interpreter_call.outputs`: Includes the outputs of python code execution in code interpreter tool call items.
  *   - `computer_call_output.output.image_url`: Include image urls from the computer call output.
  *   - `file_search_call.results`: Include the search results of the file search tool call.
  *   - `message.input_image.image_url`: Include image urls from the input message.
  *   - `message.output_text.logprobs`: Include logprobs with assistant messages.
  *   - `reasoning.encrypted_content`: Includes an encrypted version of reasoning tokens in reasoning item outputs.
  * @param input
  *   Text, image, or file inputs to the model, used to generate a response.
  * @param instructions
  *   A system (or developer) message inserted into the model's context.
  * @param maxOutputTokens
  *   An upper bound for the number of tokens that can be generated for a response, including visible output tokens and reasoning tokens.
  * @param maxToolCalls
  *   The maximum number of total calls to built-in tools that can be processed in a response.
  * @param metadata
  *   Set of 16 key-value pairs that can be attached to an object. Keys are strings with a maximum length of 64 characters. Values are
  *   strings with a maximum length of 512 characters.
  * @param model
  *   Model ID used to generate the response, like gpt-4o or o3.
  * @param parallelToolCalls
  *   Whether to allow the model to run tool calls in parallel. Defaults to true.
  * @param previousResponseId
  *   The unique ID of the previous response to the model. Use this to create multi-turn conversations.
  * @param prompt
  *   Reference to a prompt template and its variables.
  * @param promptCacheKey
  *   Used by OpenAI to cache responses for similar requests to optimize your cache hit rates.
  * @param reasoning
  *   Configuration options for reasoning models (o-series models only).
  * @param safetyIdentifier
  *   A stable identifier used to help detect users of your application that may be violating OpenAI's usage policies.
  * @param serviceTier
  *   Specifies the processing type used for serving the request. Defaults to 'auto'.
  * @param store
  *   Whether to store the generated model response for later retrieval via API. Defaults to true.
  * @param stream
  *   If set to true, the model response data will be streamed to the client as it is generated using server-sent events. Defaults to false.
  * @param temperature
  *   What sampling temperature to use, between 0 and 2. Defaults to 1.
  * @param text
  *   Configuration options for a text response from the model. Can be plain text or structured JSON data.
  * @param toolChoice
  *   How the model should select which tool (or tools) to use when generating a response.
  * @param tools
  *   An array of tools the model may call while generating a response.
  * @param topLogprobs
  *   An integer between 0 and 20 specifying the number of most likely tokens to return at each token position.
  * @param topP
  *   An alternative to sampling with temperature, called nucleus sampling. Defaults to 1.
  * @param truncation
  *   The truncation strategy to use for the model response. Defaults to 'disabled'.
  * @param user
  *   Deprecated. Use safetyIdentifier and promptCacheKey instead.
  * @param promptCacheRetention
  *   Can be used to specify policy on how long the prompt cache should be retained, not every model support every policy, check the API
  *   documentation for more details.
  */
case class ResponsesRequestBody(
    background: Option[Boolean] = None,
    include: Option[List[String]] = None,
    input: Option[Either[String, List[Input]]] = None,
    instructions: Option[String] = None,
    maxOutputTokens: Option[Int] = None,
    maxToolCalls: Option[Int] = None,
    metadata: Option[Map[String, String]] = None,
    model: Option[ResponsesModel] = None,
    parallelToolCalls: Option[Boolean] = None,
    previousResponseId: Option[String] = None,
    prompt: Option[ResponsesRequestBody.PromptConfig] = None,
    promptCacheKey: Option[String] = None,
    reasoning: Option[ResponsesRequestBody.ReasoningConfig] = None,
    safetyIdentifier: Option[String] = None,
    serviceTier: Option[String] = None,
    store: Option[Boolean] = None,
    stream: Option[Boolean] = None,
    temperature: Option[Double] = None,
    text: Option[ResponsesRequestBody.TextConfig] = None,
    toolChoice: Option[ToolChoice] = None,
    tools: Option[List[Tool]] = None,
    topLogprobs: Option[Int] = None,
    topP: Option[Double] = None,
    truncation: Option[String] = None,
    user: Option[String] = None,
    promptCacheRetention: Option[CacheRetentionPolicy] = None
)

object ResponsesRequestBody {

  case class PromptConfig(
      id: String,
      variables: Option[Map[String, String]] = None,
      version: Option[String] = None
  )

  case class ReasoningConfig(
      effort: Option[String] = None,
      summary: Option[String] = None
  )

  sealed trait Input
  object Input {
    sealed trait InputContentItem
    object InputContentItem {
      case class InputText(text: String) extends InputContentItem
      case class InputImage(detail: String, fileId: Option[String], imageUrl: Option[String]) extends InputContentItem
      case class InputFile(fileData: Option[String], fileId: Option[String], fileUrl: Option[String], filename: Option[String])
          extends InputContentItem

    }

    sealed trait OutputContentItem

    object OutputContentItem {
      object OutputText {
        sealed trait Annotation

        object Annotation {
          case class FileCitation(fileId: String, filename: String, index: Int) extends Annotation
          case class UrlCitation(endIndex: Int, startIndex: Int, title: String, url: String) extends Annotation
          case class ContainerFileCitation(containerId: String, endIndex: Int, fileId: String, filename: String, startIndex: Int)
              extends Annotation
          case class FilePath(fileId: String, index: Int) extends Annotation

        }

        case class TopLogProb(bytes: List[Byte], logprob: Double, token: String)
        case class LogProb(bytes: List[Byte], logprob: Double, token: String, topLogprobs: List[TopLogProb])
      }

      case class OutputText(annotations: List[Annotation], text: String, logprobs: Option[List[LogProb]] = None) extends OutputContentItem
      case class Refusal(refusal: String) extends OutputContentItem

    }

    sealed trait Message extends Input

    case class InputMessage(content: List[InputContentItem], role: String, status: Option[String]) extends Message

    case class OutputMessage(content: List[OutputContentItem], id: String, role: String, status: String) extends Message

    object FileSearchCall {
      case class FileSearchResult(
          attributes: Option[Map[String, String]] = None,
          fileId: Option[String] = None,
          filename: Option[String] = None,
          score: Option[Double] = None,
          text: Option[String] = None
      )
    }

    case class FileSearchCall(
        id: String,
        queries: List[String],
        status: String,
        results: Option[List[FileSearchCall.FileSearchResult]] = None
    ) extends Input

    object ComputerCall {
      case class PendingSafetyCheck(code: String, id: String, message: String, status: String)
      sealed trait Action
      object Action {
        case class Click(button: String, x: Int, y: Int) extends Action
        case class DoubleClick(x: Int, y: Int) extends Action
        case class Drag(path: List[Map[String, Int]]) extends Action
        case class Keypress(keys: List[String]) extends Action
        case class Move(x: Int, y: Int) extends Action
        case class Screenshot() extends Action
        case class Scroll(scrollX: Int, scrollY: Int, x: Int, y: Int) extends Action
        case class Type(text: String) extends Action
        case class Wait() extends Action

      }
    }

    case class ComputerCall(
        action: ComputerCall.Action,
        callId: String,
        id: String,
        pendingSafetyChecks: List[ComputerCall.PendingSafetyCheck]
    ) extends Input

    object ComputerCallOutput {
      case class ComputerScreenshot(fileId: Option[String] = None, imageUrl: Option[String] = None)
      case class AcknowledgedSafetyCheck(id: String, message: Option[String] = None, code: Option[String] = None)
    }

    case class ComputerCallOutput(
        callId: String,
        output: ComputerCallOutput.ComputerScreenshot,
        acknowledgedSafetyChecks: Option[List[ComputerCallOutput.AcknowledgedSafetyCheck]] = None,
        id: Option[String] = None,
        status: Option[String] = None
    ) extends Input

    object WebSearchCall {
      sealed trait Action

      object Action {
        case class Search(query: String) extends Action
        case class OpenPage(url: String) extends Action
        case class Find(pattern: String, url: String) extends Action

      }
    }

    case class WebSearchCall(action: WebSearchCall.Action, id: String, status: String) extends Input

    case class FunctionCall(arguments: String, callId: String, name: String, id: Option[String] = None, status: Option[String] = None)
        extends Input

    case class FunctionCallOutput(callId: String, output: String, id: Option[String] = None, status: Option[String] = None) extends Input

    object Reasoning {
      case class SummaryText(text: String)
    }

    case class Reasoning(
        id: String,
        summary: List[Reasoning.SummaryText],
        encryptedContent: Option[String] = None,
        status: Option[String] = None
    ) extends Input

    case class ImageGenerationCall(id: String, result: Option[String], status: String) extends Input

    object CodeInterpreterCall {
      sealed trait Output

      object Output {
        case class Logs(logs: String) extends Output
        case class Image(url: String) extends Output

      }
    }

    case class CodeInterpreterCall(
        code: Option[String],
        containerId: String,
        id: String,
        outputs: Option[List[CodeInterpreterCall.Output]] = None,
        status: String
    ) extends Input

    object LocalShellCall {
      case class Action(
          command: List[String],
          env: Map[String, String],
          timeoutMs: Option[Int] = None,
          user: Option[String] = None,
          workingDirectory: Option[String] = None
      )
    }

    case class LocalShellCall(action: LocalShellCall.Action, callId: String, id: String, status: String) extends Input

    case class LocalShellCallOutput(id: String, output: String, status: Option[String] = None) extends Input

    object McpListTools {
      case class Tool(inputSchema: Schema, name: String, annotations: Option[Json] = None, description: Option[String] = None)

      object Tool {

        /** Create an MCP Tool with schema automatically generated from type T.
          *
          * @param name
          *   The name of the tool.
          * @param description
          *   A description of the tool.
          * @param annotations
          *   Optional annotations for the tool.
          * @tparam T
          *   The type to generate schema from.
          * @return
          *   An MCP Tool with auto-generated input schema.
          */
        def withTapirSchema[T: TSchema](name: String, description: Option[String] = None, annotations: Option[Json] = None): Tool = {
          val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
          Tool(schema, name, annotations, description)
        }
      }
    }

    case class McpListTools(id: String, serverLabel: String, tools: List[McpListTools.Tool], error: Option[String] = None) extends Input

    case class McpApprovalRequest(arguments: String, id: String, name: String, serverLabel: String) extends Input

    case class McpApprovalResponse(approvalRequestId: String, approve: Boolean, id: Option[String] = None, reason: Option[String] = None)
        extends Input

    case class McpToolCall(
        arguments: String,
        id: String,
        name: String,
        serverLabel: String,
        error: Option[String] = None,
        output: Option[String] = None
    ) extends Input

    case class ItemReference(id: String) extends Input
  }

  sealed trait Format

  object Format {
    case class Text() extends Format
    case class JsonObject() extends Format
    case class JsonSchema(name: String, strict: Option[Boolean], schema: Option[Schema], description: Option[String]) extends Format

    object JsonSchema {

      /** Create a JsonSchema format with schema automatically generated from type T.
        *
        * @param name
        *   The name of the response format.
        * @param description
        *   A description of what the response format is for.
        * @param strict
        *   Whether to enable strict schema adherence.
        * @tparam T
        *   The type to generate schema from.
        * @return
        *   A JsonSchema format with auto-generated schema.
        */
      def withTapirSchema[T: TSchema](name: String, description: Option[String] = None, strict: Option[Boolean] = None): JsonSchema = {
        val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
        JsonSchema(name, strict, Some(schema), description)
      }
    }

  }

  case class TextConfig(
      format: Option[Format] = None
  )
}
