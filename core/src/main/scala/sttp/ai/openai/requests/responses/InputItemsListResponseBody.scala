package sttp.openai.requests.responses

import sttp.apispec.Schema
import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.chat.SchemaSupport
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TSchema}
import ujson.Value

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
      @upickle.implicits.key("input_text")
      case class InputText(text: String) extends InputContent

      @upickle.implicits.key("input_image")
      case class InputImage(detail: String, fileId: Option[String] = None, imageUrl: Option[String] = None) extends InputContent

      @upickle.implicits.key("input_file")
      case class InputFile(
          fileData: Option[String] = None,
          fileId: Option[String] = None,
          fileUrl: Option[String] = None,
          filename: Option[String] = None
      ) extends InputContent

      implicit val inputTextR: SnakePickle.Reader[InputText] = SnakePickle.macroR
      implicit val inputImageR: SnakePickle.Reader[InputImage] = SnakePickle.macroR
      implicit val inputFileR: SnakePickle.Reader[InputFile] = SnakePickle.macroR
      implicit val inputContentR: SnakePickle.Reader[InputContent] = SnakePickle.macroR
    }

    sealed trait OutputContent
    object OutputContent {
      sealed trait Annotation
      object Annotation {
        @upickle.implicits.key("file_citation")
        case class FileCitation(fileId: String, filename: String, index: Int) extends Annotation

        @upickle.implicits.key("url_citation")
        case class UrlCitation(endIndex: Int, startIndex: Int, title: String, url: String) extends Annotation

        @upickle.implicits.key("container_file_citation")
        case class ContainerFileCitation(containerId: String, endIndex: Int, fileId: String, filename: String, startIndex: Int)
            extends Annotation

        @upickle.implicits.key("file_path")
        case class FilePath(fileId: String, index: Int) extends Annotation

        implicit val fileCitationR: SnakePickle.Reader[FileCitation] = SnakePickle.macroR
        implicit val urlCitationR: SnakePickle.Reader[UrlCitation] = SnakePickle.macroR
        implicit val containerFileCitationR: SnakePickle.Reader[ContainerFileCitation] = SnakePickle.macroR
        implicit val filePathR: SnakePickle.Reader[FilePath] = SnakePickle.macroR
        implicit val annotationR: SnakePickle.Reader[Annotation] = SnakePickle.macroR
      }

      case class LogProb(bytes: List[Byte], logprob: Double, token: String, topLogprobs: List[TopLogProb])
      case class TopLogProb(bytes: List[Byte], logprob: Double, token: String)

      @upickle.implicits.key("output_text")
      case class OutputText(annotations: List[Annotation], text: String, logprobs: Option[List[LogProb]] = None) extends OutputContent

      @upickle.implicits.key("refusal")
      case class Refusal(refusal: String) extends OutputContent

      implicit val topLogProbR: SnakePickle.Reader[TopLogProb] = SnakePickle.macroR
      implicit val logProbR: SnakePickle.Reader[LogProb] = SnakePickle.macroR
      implicit val outputTextR: SnakePickle.Reader[OutputText] = SnakePickle.macroR
      implicit val refusalR: SnakePickle.Reader[Refusal] = SnakePickle.macroR
      implicit val outputContentR: SnakePickle.Reader[OutputContent] = SnakePickle.macroR
    }

    case class FileSearchResult(
        attributes: Option[Map[String, String]] = None,
        fileId: Option[String] = None,
        filename: Option[String] = None,
        score: Option[Double] = None,
        text: Option[String] = None
    )

    object ComputerToolCall {
      case class PendingSafetyCheck(code: String, id: String, message: String)

      sealed trait Action
      object Action {
        @upickle.implicits.key("click")
        case class Click(button: String, x: Int, y: Int) extends Action
        @upickle.implicits.key("double_click")
        case class DoubleClick(x: Int, y: Int) extends Action
        @upickle.implicits.key("drag")
        case class Drag(path: List[Map[String, Int]]) extends Action
        @upickle.implicits.key("keypress")
        case class KeyPress(keys: List[String]) extends Action
        @upickle.implicits.key("move")
        case class Move(x: Int, y: Int) extends Action
        @upickle.implicits.key("screenshot")
        case class Screenshot() extends Action
        @upickle.implicits.key("scroll")
        case class Scroll(scrollX: Int, scrollY: Int, x: Int, y: Int) extends Action
        @upickle.implicits.key("type")
        case class Type(text: String) extends Action
        @upickle.implicits.key("wait")
        case class Wait() extends Action

        implicit val clickR: SnakePickle.Reader[Click] = SnakePickle.macroR
        implicit val doubleClickR: SnakePickle.Reader[DoubleClick] = SnakePickle.macroR
        implicit val dragR: SnakePickle.Reader[Drag] = SnakePickle.macroR
        implicit val keyPressR: SnakePickle.Reader[KeyPress] = SnakePickle.macroR
        implicit val moveR: SnakePickle.Reader[Move] = SnakePickle.macroR
        implicit val screenshotR: SnakePickle.Reader[Screenshot] = SnakePickle.macroR
        implicit val scrollR: SnakePickle.Reader[Scroll] = SnakePickle.macroR
        implicit val typeR: SnakePickle.Reader[Type] = SnakePickle.macroR
        implicit val waitR: SnakePickle.Reader[Wait] = SnakePickle.macroR
        implicit val actionR: SnakePickle.Reader[Action] = SnakePickle.macroR
      }

      implicit val pendingSafetyCheckR: SnakePickle.Reader[PendingSafetyCheck] = SnakePickle.macroR
    }

    object ComputerToolCallOutput {
      @upickle.implicits.key("computer_screenshot")
      case class ComputerScreenshot(fileId: String, imageUrl: String)

      case class AcknowledgedSafetyCheck(id: String, code: Option[String] = None, message: Option[String] = None)

      implicit val computerScreenshotR: SnakePickle.Reader[ComputerScreenshot] = SnakePickle.macroR
      implicit val acknowledgedSafetyCheckR: SnakePickle.Reader[AcknowledgedSafetyCheck] = SnakePickle.macroR
    }

    object WebSearchToolCall {
      sealed trait Action
      object Action {
        @upickle.implicits.key("search")
        case class Search(query: String) extends Action
        @upickle.implicits.key("open_page")
        case class OpenPage(url: String) extends Action
        @upickle.implicits.key("find")
        case class Find(pattern: String, url: String) extends Action

        implicit val searchR: SnakePickle.Reader[Search] = SnakePickle.macroR
        implicit val openPageR: SnakePickle.Reader[OpenPage] = SnakePickle.macroR
        implicit val findR: SnakePickle.Reader[Find] = SnakePickle.macroR
        implicit val actionR: SnakePickle.Reader[Action] = SnakePickle.macroR
      }
    }

    object LocalShellCall {
      @upickle.implicits.key("exec")
      case class Action(
          command: List[String],
          env: Map[String, String],
          timeoutMs: Option[Int] = None,
          user: Option[String] = None,
          workingDirectory: Option[String] = None
      )

      implicit val actionR: SnakePickle.Reader[Action] = SnakePickle.macroR
    }

    object McpListTools {
      implicit private val schemaR: SnakePickle.Reader[Schema] = SchemaSupport.schemaRW
      case class Tool(inputSchema: Schema, name: String, annotations: Option[Value] = None, description: Option[String] = None)

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
        def withTapirSchema[T: TSchema](name: String, description: Option[String] = None, annotations: Option[Value] = None): Tool = {
          val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
          Tool(schema, name, annotations, description)
        }
      }

      implicit val toolR: SnakePickle.Reader[Tool] = SnakePickle.macroR
    }

    object CodeInterpreterToolCall {
      sealed trait Output
      object Output {
        @upickle.implicits.key("logs")
        case class Logs(logs: String) extends Output
        @upickle.implicits.key("image")
        case class Image(url: String) extends Output

        implicit val logsR: SnakePickle.Reader[Logs] = SnakePickle.macroR
        implicit val imageR: SnakePickle.Reader[Image] = SnakePickle.macroR
        implicit val outputR: SnakePickle.Reader[Output] = SnakePickle.macroR
      }
    }

    @upickle.implicits.key("message")
    case class InputMessage(content: List[InputContent], id: String, role: String, status: String) extends InputItem

    @upickle.implicits.key("message")
    case class OutputMessage(content: List[OutputContent], id: String, role: String, status: String) extends InputItem

    @upickle.implicits.key("file_search_call")
    case class FileSearchToolCall(id: String, queries: List[String], status: String, results: Option[List[FileSearchResult]] = None)
        extends InputItem

    @upickle.implicits.key("computer_call")
    case class ComputerToolCall(
        action: ComputerToolCall.Action,
        callId: String,
        id: String,
        pendingSafetyChecks: List[ComputerToolCall.PendingSafetyCheck],
        status: String
    ) extends InputItem

    @upickle.implicits.key("computer_call_output")
    case class ComputerToolCallOutput(
        callId: String,
        id: String,
        output: ComputerToolCallOutput.ComputerScreenshot,
        acknowledgedSafetyChecks: Option[List[ComputerToolCallOutput.AcknowledgedSafetyCheck]] = None,
        status: String
    ) extends InputItem

    @upickle.implicits.key("web_search_call")
    case class WebSearchToolCall(action: WebSearchToolCall.Action, id: String, status: String) extends InputItem

    @upickle.implicits.key("function_call")
    case class FunctionToolCall(arguments: String, callId: String, id: String, name: String, status: String) extends InputItem

    @upickle.implicits.key("function_call_output")
    case class FunctionToolCallOutput(callId: String, id: String, output: String, status: String) extends InputItem

    @upickle.implicits.key("image_generation_call")
    case class ImageGenerationCall(id: String, result: Option[String], status: String) extends InputItem

    @upickle.implicits.key("code_interpreter_call")
    case class CodeInterpreterToolCall(
        code: Option[String],
        containerId: String,
        id: String,
        outputs: Option[List[CodeInterpreterToolCall.Output]] = None,
        status: String
    ) extends InputItem

    @upickle.implicits.key("local_shell_call")
    case class LocalShellCall(action: LocalShellCall.Action, callId: String, id: String, status: String) extends InputItem

    @upickle.implicits.key("local_shell_call_output")
    case class LocalShellCallOutput(id: String, output: String, status: Option[String] = None) extends InputItem

    @upickle.implicits.key("mcp_list_tools")
    case class McpListTools(id: String, serverLabel: String, tools: List[McpListTools.Tool], error: Option[String] = None) extends InputItem

    @upickle.implicits.key("mcp_approval_request")
    case class McpApprovalRequest(arguments: String, id: String, name: String, serverLabel: String) extends InputItem

    @upickle.implicits.key("mcp_approval_response")
    case class McpApprovalResponse(approvalRequestId: String, approve: Boolean, id: String, reason: Option[String] = None) extends InputItem

    @upickle.implicits.key("mcp_tool_call")
    case class McpToolCall(
        arguments: String,
        id: String,
        name: String,
        serverLabel: String,
        error: Option[String] = None,
        output: Option[String] = None
    ) extends InputItem

    implicit val fileSearchResultR: SnakePickle.Reader[FileSearchResult] = SnakePickle.macroR
    implicit val inputMessageR: SnakePickle.Reader[InputMessage] = SnakePickle.macroR
    implicit val outputMessageR: SnakePickle.Reader[OutputMessage] = SnakePickle.macroR
    implicit val fileSearchToolCallR: SnakePickle.Reader[FileSearchToolCall] = SnakePickle.macroR
    implicit val computerToolCallR: SnakePickle.Reader[ComputerToolCall] = SnakePickle.macroR
    implicit val computerToolCallOutputR: SnakePickle.Reader[ComputerToolCallOutput] = SnakePickle.macroR
    implicit val webSearchToolCallR: SnakePickle.Reader[WebSearchToolCall] = SnakePickle.macroR
    implicit val functionToolCallR: SnakePickle.Reader[FunctionToolCall] = SnakePickle.macroR
    implicit val functionToolCallOutputR: SnakePickle.Reader[FunctionToolCallOutput] = SnakePickle.macroR
    implicit val imageGenerationCallR: SnakePickle.Reader[ImageGenerationCall] = SnakePickle.macroR
    implicit val codeInterpreterToolCallR: SnakePickle.Reader[CodeInterpreterToolCall] = SnakePickle.macroR
    implicit val localShellCallR: SnakePickle.Reader[LocalShellCall] = SnakePickle.macroR
    implicit val localShellCallOutputR: SnakePickle.Reader[LocalShellCallOutput] = SnakePickle.macroR
    implicit val mcpListToolsR: SnakePickle.Reader[McpListTools] = SnakePickle.macroR
    implicit val mcpApprovalRequestR: SnakePickle.Reader[McpApprovalRequest] = SnakePickle.macroR
    implicit val mcpApprovalResponseR: SnakePickle.Reader[McpApprovalResponse] = SnakePickle.macroR
    implicit val mcpToolCallR: SnakePickle.Reader[McpToolCall] = SnakePickle.macroR
    implicit val inputItemR: SnakePickle.Reader[InputItem] = SnakePickle.macroR
  }

  implicit val inputItemsListResponseBodyR: SnakePickle.Reader[InputItemsListResponseBody] = SnakePickle.macroR
}
