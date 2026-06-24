package sttp.ai.openai.requests.responses

import io.circe.Json
import sttp.apispec.Schema
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TSchema}

/** Response body for input items list API endpoint.
  *
  * @param data
  *   A list of items used to generate this response.
  * @param firstId
  *   The ID of the first item in the list.
  * @param hasMore
  *   Whether there are more items available.
  * @param lastId
  *   The ID of the last item in the list.
  * @param `object`
  *   The type of object returned, must be list.
  */
case class InputItemsListResponseBody(
    data: List[InputItemsListResponseBody.InputItem],
    firstId: String,
    hasMore: Boolean,
    lastId: String,
    `object`: String
)

object InputItemsListResponseBody {

  sealed trait InputItem
  object InputItem {

    sealed trait InputContent
    object InputContent {
      case class InputText(text: String) extends InputContent
      case class InputImage(detail: String, fileId: Option[String] = None, imageUrl: Option[String] = None) extends InputContent
      case class InputFile(
          fileData: Option[String] = None,
          fileId: Option[String] = None,
          fileUrl: Option[String] = None,
          filename: Option[String] = None
      ) extends InputContent

    }

    sealed trait OutputContent
    object OutputContent {
      sealed trait Annotation
      object Annotation {
        case class FileCitation(fileId: String, filename: String, index: Int) extends Annotation
        case class UrlCitation(endIndex: Int, startIndex: Int, title: String, url: String) extends Annotation
        case class ContainerFileCitation(containerId: String, endIndex: Int, fileId: String, filename: String, startIndex: Int)
            extends Annotation
        case class FilePath(fileId: String, index: Int) extends Annotation

      }

      case class LogProb(bytes: List[Byte], logprob: Double, token: String, topLogprobs: List[TopLogProb])
      case class TopLogProb(bytes: List[Byte], logprob: Double, token: String)

      case class OutputText(annotations: List[Annotation], text: String, logprobs: Option[List[LogProb]] = None) extends OutputContent
      case class Refusal(refusal: String) extends OutputContent

    }

    case class FileSearchResult(
        attributes: Option[Map[String, String]] = None,
        fileId: Option[String] = None,
        filename: Option[String] = None,
        score: Option[Double] = None,
        text: Option[String] = None
    )

    object ComputerCall {
      case class PendingSafetyCheck(code: String, id: String, message: String)

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

    object ComputerCallOutput {
      case class ComputerScreenshot(fileId: String, imageUrl: String)
      case class AcknowledgedSafetyCheck(id: String, code: Option[String] = None, message: Option[String] = None)
    }

    object WebSearchCall {
      sealed trait Action
      object Action {
        case class Search(query: String) extends Action
        case class OpenPage(url: String) extends Action
        case class Find(pattern: String, url: String) extends Action

      }
    }

    object LocalShellCall {
      case class Action(
          command: List[String],
          env: Map[String, String],
          timeoutMs: Option[Int] = None,
          user: Option[String] = None,
          workingDirectory: Option[String] = None
      )
    }

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

    object CodeInterpreterCall {
      sealed trait Output
      object Output {
        case class Logs(logs: String) extends Output
        case class Image(url: String) extends Output

      }
    }

    sealed trait Message extends InputItem

    case class InputMessage(content: List[InputContent], id: String, role: String, status: String) extends Message

    case class OutputMessage(content: List[OutputContent], id: String, role: String, status: String) extends Message

    case class FileSearchCall(id: String, queries: List[String], status: String, results: Option[List[FileSearchResult]] = None)
        extends InputItem

    case class ComputerCall(
        action: ComputerCall.Action,
        callId: String,
        id: String,
        pendingSafetyChecks: List[ComputerCall.PendingSafetyCheck],
        status: String
    ) extends InputItem

    case class ComputerCallOutput(
        callId: String,
        id: String,
        output: ComputerCallOutput.ComputerScreenshot,
        acknowledgedSafetyChecks: Option[List[ComputerCallOutput.AcknowledgedSafetyCheck]] = None,
        status: String
    ) extends InputItem

    case class WebSearchCall(action: WebSearchCall.Action, id: String, status: String) extends InputItem

    case class FunctionCall(arguments: String, callId: String, id: String, name: String, status: String) extends InputItem

    case class FunctionCallOutput(callId: String, id: String, output: String, status: String) extends InputItem

    case class ImageGenerationCall(id: String, result: Option[String], status: String) extends InputItem

    case class CodeInterpreterCall(
        code: Option[String],
        containerId: String,
        id: String,
        outputs: Option[List[CodeInterpreterCall.Output]] = None,
        status: String
    ) extends InputItem

    case class LocalShellCall(action: LocalShellCall.Action, callId: String, id: String, status: String) extends InputItem

    case class LocalShellCallOutput(id: String, output: String, status: Option[String] = None) extends InputItem

    case class McpListTools(id: String, serverLabel: String, tools: List[McpListTools.Tool], error: Option[String] = None) extends InputItem

    case class McpApprovalRequest(arguments: String, id: String, name: String, serverLabel: String) extends InputItem

    case class McpApprovalResponse(approvalRequestId: String, approve: Boolean, id: String, reason: Option[String] = None) extends InputItem

    case class McpToolCall(
        arguments: String,
        id: String,
        name: String,
        serverLabel: String,
        error: Option[String] = None,
        output: Option[String] = None
    ) extends InputItem

  }
}
