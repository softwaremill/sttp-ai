package sttp.ai.openai.requests.responses

import io.circe.Json
import sttp.ai.openai.requests.completions.chat.SchemaSupport
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TSchema}

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
  case class Function(
      name: String,
      parameters: Map[String, Json],
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
    def withTapirSchema[T: TSchema](name: String, description: Option[String] = None): Function =
      withTapirSchema(name, description, strict = true)

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
      val schemaJson = if (strict) SchemaSupport.normalizeForStrict(schema) else SchemaSupport.schemaCodec(schema)
      Function(name, schemaJson.asObject.map(_.toMap).getOrElse(Map.empty), strict, description)
    }
  }

  object FileSearch {

    sealed trait Filter

    object Filter {
      case class Metadata(metadata: Map[String, String]) extends Filter
      case class FileIds(fileIds: List[String]) extends Filter
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
    case class DefaultWebSearchPreview(
        searchContextSize: Option[String] = None,
        userLocation: Option[UserLocation] = None
    ) extends WebSearchPreview

    case class WebSearchPreview20250311(
        searchContextSize: Option[String] = None,
        userLocation: Option[UserLocation] = None
    ) extends WebSearchPreview
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
  case class ComputerUsePreview(
      displayHeight: Int,
      displayWidth: Int,
      environment: String
  ) extends Tool

  object Mcp {

    sealed trait ApprovalFilter

    object ApprovalFilter {
      case class Always(toolNames: Option[List[String]] = None) extends ApprovalFilter
      case class Never(toolNames: Option[List[String]] = None) extends ApprovalFilter
    }

    sealed trait RequireApproval

    object RequireApproval {
      case object Always extends RequireApproval
      case object Never extends RequireApproval
      case class Filter(always: Option[ApprovalFilter.Always] = None, never: Option[ApprovalFilter.Never] = None) extends RequireApproval
    }

    sealed trait AllowedTools

    object AllowedTools {
      case class ToolList(tools: List[String]) extends AllowedTools
      case class FilterObject(filter: Map[String, Json]) extends AllowedTools

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
          val schemaJson = SchemaSupport.schemaCodec(schema)
          FilterObject(schemaJson.asObject.map(_.toMap).getOrElse(Map.empty))
        }
      }
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
  case class Mcp(
      serverLabel: String,
      serverUrl: String,
      allowedTools: Option[Mcp.AllowedTools] = None,
      headers: Option[Map[String, String]] = None,
      requireApproval: Option[Mcp.RequireApproval] = None,
      serverDescription: Option[String] = None
  ) extends Tool

  object CodeInterpreter {

    sealed trait Container

    object Container {
      case class ContainerAuto(fileIds: Option[List[String]] = None) extends Container
      case class ContainerId(id: String) extends Container
    }
  }

  /** Code interpreter tool
    *
    * @param container
    *   The code interpreter container.
    */
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

  case class LocalShell() extends Tool

  object Custom {

    sealed trait Format

    object Format {
      case class Text() extends Format

      /** Grammar format
        *
        * @param definition
        *   The grammar definition.
        * @param syntax
        *   The syntax of the grammar definition.
        */
      case class Grammar(
          definition: String,
          syntax: String
      ) extends Format
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
  case class Custom(
      name: String,
      description: Option[String] = None,
      format: Option[Custom.Format] = None
  ) extends Tool
}
