package sttp.openai.requests.responses

import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.chat.SchemaSupport
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TSchema}
import ujson.Value

sealed trait Tool

object Tool {

  /** Function tool definition
    *
    * @param name
    *   The name of the function to call.
    * @param parameters
    *   A JSON schema object describing the parameters of the function.
    * @param strict
    *   Whether to enforce strict parameter validation. Default true.
    * @param description
    *   A description of the function. Used by the model to determine whether or not to call the function.
    */
  @upickle.implicits.key("function")
  case class Function(
      name: String,
      parameters: Map[String, Value],
      strict: Boolean = true,
      description: Option[String] = None
  ) extends Tool

  object Function {

    /** Create a Function tool with schema automatically generated from type T.
      *
      * @param name
      *   The name of the function to call.
      * @param description
      *   A description of the function.
      * @tparam T
      *   The type to generate schema from.
      * @return
      *   A Function tool with auto-generated schema.
      */
    def withTapirSchema[T: TSchema](name: String, description: Option[String] = None): Function = {
      val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
      val schemaJson = SnakePickle.writeJs(schema)(SchemaSupport.schemaRW)
      Function(name, schemaJson.obj.toMap, strict = true, description)
    }

    /** Create a Function tool with schema automatically generated from type T and custom strict flag.
      *
      * @param name
      *   The name of the function to call.
      * @param description
      *   A description of the function.
      * @param strict
      *   Whether to enforce strict parameter validation.
      * @tparam T
      *   The type to generate schema from.
      * @return
      *   A Function tool with auto-generated schema and custom strict flag.
      */
    def withTapirSchema[T: TSchema](name: String, description: Option[String], strict: Boolean): Function = {
      val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
      val schemaJson = SnakePickle.writeJs(schema)(SchemaSupport.schemaRW)
      Function(name, schemaJson.obj.toMap, strict, description)
    }
  }

  object FileSearch {

    sealed trait Filter

    object Filter {
      @upickle.implicits.key("metadata")
      case class Metadata(metadata: Map[String, String]) extends Filter

      @upickle.implicits.key("file_ids")
      case class FileIds(fileIds: List[String]) extends Filter

      implicit val metadataRW: SnakePickle.ReadWriter[Metadata] = SnakePickle.macroRW
      implicit val fileIdsRW: SnakePickle.ReadWriter[FileIds] = SnakePickle.macroRW
      implicit val filterRW: SnakePickle.ReadWriter[Filter] = SnakePickle.macroRW
    }

    /** Ranking options for file search
      *
      * @param ranker
      *   The ranker to use for the file search.
      * @param scoreThreshold
      *   The score threshold for the file search, a number between 0 and 1.
      */
    case class RankingOptions(
        ranker: Option[String] = None,
        scoreThreshold: Option[Double] = None
    )

    implicit val rankingOptionsRW: SnakePickle.ReadWriter[RankingOptions] = SnakePickle.macroRW
  }

  /** File search tool
    *
    * @param vectorStoreIds
    *   The IDs of the vector stores to search.
    * @param filters
    *   A filter to apply.
    * @param maxNumResults
    *   The maximum number of results to return. This number should be between 1 and 50 inclusive.
    * @param rankingOptions
    *   Ranking options for search.
    */
  @upickle.implicits.key("file_search")
  case class FileSearch(
      vectorStoreIds: List[String],
      filters: Option[FileSearch.Filter] = None,
      maxNumResults: Option[Int] = None,
      rankingOptions: Option[FileSearch.RankingOptions] = None
  ) extends Tool

  /** User location for web search
    *
    * @param city
    *   Free text input for the city of the user, e.g. San Francisco.
    * @param country
    *   The two-letter ISO country code of the user, e.g. US.
    * @param region
    *   Free text input for the region of the user, e.g. California.
    * @param timezone
    *   The IANA timezone of the user, e.g. America/Los_Angeles.
    */
  case class UserLocation(
      city: Option[String] = None,
      country: Option[String] = None,
      region: Option[String] = None,
      timezone: Option[String] = None
  )

  /** Web search preview tool
    *
    * @param searchContextSize
    *   High level guidance for the amount of context window space to use for the search.
    * @param userLocation
    *   The user's location.
    */
  sealed trait WebSearchPreview extends Tool {
    def searchContextSize: Option[String]
    def userLocation: Option[UserLocation]
  }

  object WebSearchPreview {
    @upickle.implicits.key("web_search_preview")
    case class DefaultWebSearchPreview(
        searchContextSize: Option[String] = None,
        userLocation: Option[UserLocation] = None
    ) extends WebSearchPreview

    @upickle.implicits.key("web_search_preview_2025_03_11")
    case class WebSearchPreview20250311(
        searchContextSize: Option[String] = None,
        userLocation: Option[UserLocation] = None
    ) extends WebSearchPreview

    implicit val defaultWebSearchPreviewRW: SnakePickle.ReadWriter[DefaultWebSearchPreview] = SnakePickle.macroRW
    implicit val webSearchPreview20250311RW: SnakePickle.ReadWriter[WebSearchPreview20250311] = SnakePickle.macroRW
    implicit val webSearchPreviewRW: SnakePickle.ReadWriter[WebSearchPreview] = SnakePickle.macroRW
  }

  /** Computer use preview tool
    *
    * @param displayHeight
    *   The height of the computer display.
    * @param displayWidth
    *   The width of the computer display.
    * @param environment
    *   The type of computer environment to control.
    */
  @upickle.implicits.key("computer_use_preview")
  case class ComputerUsePreview(
      displayHeight: Int,
      displayWidth: Int,
      environment: String
  ) extends Tool

  object McpTool {

    sealed trait ApprovalFilter

    object ApprovalFilter {
      case class Always(toolNames: Option[List[String]] = None) extends ApprovalFilter
      case class Never(toolNames: Option[List[String]] = None) extends ApprovalFilter

      implicit val alwaysRW: SnakePickle.ReadWriter[Always] = SnakePickle.macroRW
      implicit val neverRW: SnakePickle.ReadWriter[Never] = SnakePickle.macroRW
      implicit val approvalFilterRW: SnakePickle.ReadWriter[ApprovalFilter] = SnakePickle.macroRW
    }

    sealed trait RequireApproval

    object RequireApproval {
      case object Always extends RequireApproval
      case object Never extends RequireApproval
      case class Filter(always: Option[ApprovalFilter.Always] = None, never: Option[ApprovalFilter.Never] = None) extends RequireApproval

      implicit val filterRW: SnakePickle.ReadWriter[Filter] = SnakePickle.macroRW

      implicit val requireApprovalRW: SnakePickle.ReadWriter[RequireApproval] = SnakePickle
        .readwriter[Value]
        .bimap[RequireApproval](
          {
            case Always         => ujson.Str("always")
            case Never          => ujson.Str("never")
            case filter: Filter => SnakePickle.writeJs(filter)
          },
          {
            case ujson.Str("always") => Always
            case ujson.Str("never")  => Never
            case obj: ujson.Obj      => SnakePickle.read[Filter](obj)
            case v                   => throw new Exception(s"Invalid require approval format: $v")
          }
        )
    }

    sealed trait AllowedTools

    object AllowedTools {
      case class ToolList(tools: List[String]) extends AllowedTools
      case class FilterObject(filter: Map[String, Value]) extends AllowedTools

      object FilterObject {

        /** Create a FilterObject with schema automatically generated from type T.
          *
          * @tparam T
          *   The type to generate schema from.
          * @return
          *   A FilterObject with auto-generated schema.
          */
        def withTapirSchema[T: TSchema](): FilterObject = {
          val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
          val schemaJson = SnakePickle.writeJs(schema)(SchemaSupport.schemaRW)
          FilterObject(schemaJson.obj.toMap)
        }
      }

      implicit val allowedToolsRW: SnakePickle.ReadWriter[AllowedTools] = SnakePickle
        .readwriter[Value]
        .bimap[AllowedTools](
          {
            case ToolList(tools)      => SnakePickle.writeJs(tools)
            case FilterObject(filter) => SnakePickle.writeJs(filter)
          },
          {
            case arr: ujson.Arr => ToolList(SnakePickle.read[List[String]](arr))
            case obj: ujson.Obj => FilterObject(SnakePickle.read[Map[String, Value]](obj))
            case v              => throw new Exception(s"Invalid allowed tools format: $v")
          }
        )
    }
  }

  /** MCP tool
    *
    * @param serverLabel
    *   A label for this MCP server, used to identify it in tool calls.
    * @param serverUrl
    *   The URL for the MCP server.
    * @param allowedTools
    *   List of allowed tool names or a filter object.
    * @param headers
    *   Optional HTTP headers to send to the MCP server.
    * @param requireApproval
    *   Specify which of the MCP server's tools require approval.
    * @param serverDescription
    *   Optional description of the MCP server.
    */
  @upickle.implicits.key("mcp")
  case class McpTool(
      serverLabel: String,
      serverUrl: String,
      allowedTools: Option[McpTool.AllowedTools] = None,
      headers: Option[Map[String, String]] = None,
      requireApproval: Option[McpTool.RequireApproval] = None,
      serverDescription: Option[String] = None
  ) extends Tool

  object CodeInterpreter {

    sealed trait Container

    object Container {
      @upickle.implicits.key("auto")
      case class ContainerAuto(fileIds: Option[List[String]] = None) extends Container
      case class ContainerId(id: String) extends Container

      implicit val containerAutoRW: SnakePickle.ReadWriter[ContainerAuto] = SnakePickle.macroRW
      implicit val containerIdRW: SnakePickle.ReadWriter[ContainerId] = SnakePickle.macroRW
      implicit val containerRW: SnakePickle.ReadWriter[Container] = SnakePickle
        .readwriter[Value]
        .bimap[Container](
          {
            case auto: ContainerAuto => SnakePickle.writeJs(auto)
            case ContainerId(id)     => ujson.Str(id)
          },
          {
            case ujson.Str(id)  => ContainerId(id)
            case obj: ujson.Obj => SnakePickle.read[ContainerAuto](obj)
            case v              => throw new Exception(s"Invalid container format: $v")
          }
        )
    }
  }

  /** Code interpreter tool
    *
    * @param container
    *   The code interpreter container.
    */
  @upickle.implicits.key("code_interpreter")
  case class CodeInterpreter(
      container: CodeInterpreter.Container
  ) extends Tool

  object ImageGeneration {

    /** Input image mask for inpainting
      *
      * @param fileId
      *   File ID for the mask image.
      * @param imageUrl
      *   Base64-encoded mask image.
      */
    case class InputImageMask(
        fileId: Option[String] = None,
        imageUrl: Option[String] = None
    )

    implicit val inputImageMaskRW: SnakePickle.ReadWriter[InputImageMask] = SnakePickle.macroRW
  }

  /** Image generation tool
    *
    * @param background
    *   Background type for the generated image.
    * @param inputFidelity
    *   Control how much effort the model will exert to match the style and features.
    * @param inputImageMask
    *   Optional mask for inpainting.
    * @param model
    *   The image generation model to use.
    * @param moderation
    *   Moderation level for the generated image.
    * @param outputCompression
    *   Compression level for the output image.
    * @param outputFormat
    *   The output format of the generated image.
    * @param partialImages
    *   Number of partial images to generate in streaming mode.
    * @param quality
    *   The quality of the generated image.
    * @param size
    *   The size of the generated image.
    */
  @upickle.implicits.key("image_generation")
  case class ImageGeneration(
      background: Option[String] = Some("auto"),
      inputFidelity: Option[String] = Some("low"),
      inputImageMask: Option[ImageGeneration.InputImageMask] = None,
      model: Option[String] = Some("gpt-image-1"),
      moderation: Option[String] = Some("auto"),
      outputCompression: Option[Int] = Some(100),
      outputFormat: Option[String] = Some("png"),
      partialImages: Option[Int] = Some(0),
      quality: Option[String] = Some("auto"),
      size: Option[String] = Some("auto")
  ) extends Tool

  @upickle.implicits.key("local_shell")
  case class LocalShell() extends Tool

  object CustomTool {

    sealed trait Format

    object Format {
      @upickle.implicits.key("text")
      case class Text() extends Format

      /** Grammar format
        *
        * @param definition
        *   The grammar definition.
        * @param syntax
        *   The syntax of the grammar definition.
        */
      @upickle.implicits.key("grammar")
      case class Grammar(
          definition: String,
          syntax: String
      ) extends Format

      implicit val textRW: SnakePickle.ReadWriter[Text] = SnakePickle.macroRW
      implicit val grammarRW: SnakePickle.ReadWriter[Grammar] = SnakePickle.macroRW
      implicit val formatRW: SnakePickle.ReadWriter[Format] = SnakePickle.macroRW
    }
  }

  /** Custom tool
    *
    * @param name
    *   The name of the custom tool.
    * @param description
    *   Optional description of the custom tool.
    * @param format
    *   The input format for the custom tool.
    */
  @upickle.implicits.key("custom")
  case class CustomTool(
      name: String,
      description: Option[String] = None,
      format: Option[CustomTool.Format] = None
  ) extends Tool

  implicit val userLocationRW: SnakePickle.ReadWriter[UserLocation] = SnakePickle.macroRW
  implicit val functionRW: SnakePickle.ReadWriter[Function] = SnakePickle.macroRW
  implicit val fileSearchRW: SnakePickle.ReadWriter[FileSearch] = SnakePickle.macroRW
  implicit val computerUsePreviewRW: SnakePickle.ReadWriter[ComputerUsePreview] = SnakePickle.macroRW
  implicit val localShellRW: SnakePickle.ReadWriter[LocalShell] = SnakePickle.macroRW
  implicit val mcpToolRW: SnakePickle.ReadWriter[McpTool] = SnakePickle.macroRW
  implicit val codeInterpreterRW: SnakePickle.ReadWriter[CodeInterpreter] = SnakePickle.macroRW
  implicit val imageGenerationRW: SnakePickle.ReadWriter[ImageGeneration] = SnakePickle.macroRW
  implicit val customToolRW: SnakePickle.ReadWriter[CustomTool] = SnakePickle.macroRW

  implicit val localToolRW: SnakePickle.ReadWriter[Tool] = SnakePickle.macroRW
}
