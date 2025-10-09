package sttp.openai.requests.responses

import sttp.openai.json.SnakePickle

sealed trait ToolChoice

object ToolChoice {
  sealed trait ToolChoiceMode extends ToolChoice
  object ToolChoiceMode {
    case object NoneChoice extends ToolChoiceMode
    case object Auto extends ToolChoiceMode
    case object Required extends ToolChoiceMode
  }

  sealed trait ToolChoiceObject extends ToolChoice
  object ToolChoiceObject {
    object AllowedTools {
      sealed trait ToolDefinition
      object ToolDefinition {
        @upickle.implicits.key("function")
        case class Function(name: String) extends ToolDefinition
        @upickle.implicits.key("mcp")
        case class Mcp(serverLabel: String) extends ToolDefinition
        @upickle.implicits.key("image_generation")
        case class ImageGeneration() extends ToolDefinition

        implicit val functionRW: SnakePickle.ReadWriter[Function] = SnakePickle.macroRW
        implicit val mcpRW: SnakePickle.ReadWriter[Mcp] = SnakePickle.macroRW
        implicit val imageGenerationRW: SnakePickle.ReadWriter[ImageGeneration] = SnakePickle.macroRW
        implicit val toolDefinitionRW: SnakePickle.ReadWriter[ToolDefinition] = SnakePickle.macroRW
      }
    }

    @upickle.implicits.key("allowed_tools")
    case class AllowedTools(mode: String, tools: List[AllowedTools.ToolDefinition]) extends ToolChoiceObject
    @upickle.implicits.key("file_search")
    case class FileSearch() extends ToolChoiceObject
    @upickle.implicits.key("web_search_preview")
    case class WebSearchPreview() extends ToolChoiceObject
    @upickle.implicits.key("computer_use_preview")
    case class ComputerUsePreview() extends ToolChoiceObject
    @upickle.implicits.key("code_interpreter")
    case class CodeInterpreter() extends ToolChoiceObject
    @upickle.implicits.key("image_generation")
    case class ImageGeneration() extends ToolChoiceObject
    @upickle.implicits.key("function")
    case class Function(name: String) extends ToolChoiceObject
    @upickle.implicits.key("mcp")
    case class Mcp(serverLabel: String, name: Option[String] = None) extends ToolChoiceObject
    @upickle.implicits.key("custom")
    case class Custom(name: String) extends ToolChoiceObject

    implicit val allowedToolsRW: SnakePickle.ReadWriter[AllowedTools] = SnakePickle.macroRW
    implicit val fileSearchRW: SnakePickle.ReadWriter[FileSearch] = SnakePickle.macroRW
    implicit val webSearchPreviewRW: SnakePickle.ReadWriter[WebSearchPreview] = SnakePickle.macroRW
    implicit val computerUsePreviewRW: SnakePickle.ReadWriter[ComputerUsePreview] = SnakePickle.macroRW
    implicit val codeInterpreterRW: SnakePickle.ReadWriter[CodeInterpreter] = SnakePickle.macroRW
    implicit val imageGenerationRW: SnakePickle.ReadWriter[ImageGeneration] = SnakePickle.macroRW
    implicit val functionRW: SnakePickle.ReadWriter[Function] = SnakePickle.macroRW
    implicit val mcpRW: SnakePickle.ReadWriter[Mcp] = SnakePickle.macroRW
    implicit val customRW: SnakePickle.ReadWriter[Custom] = SnakePickle.macroRW
    implicit val toolChoiceObjectRW: SnakePickle.ReadWriter[ToolChoiceObject] = SnakePickle.macroRW
  }

  implicit val toolChoiceModeRW: SnakePickle.ReadWriter[ToolChoiceMode] = SnakePickle
    .readwriter[String]
    .bimap[ToolChoiceMode](
      {
        case ToolChoiceMode.NoneChoice => "none"
        case ToolChoiceMode.Auto       => "auto"
        case ToolChoiceMode.Required   => "required"
      },
      {
        case "none"     => ToolChoiceMode.NoneChoice
        case "auto"     => ToolChoiceMode.Auto
        case "required" => ToolChoiceMode.Required
        case other      => throw new Exception(s"Unknown tool choice mode: $other")
      }
    )

  implicit val toolChoiceRW: SnakePickle.ReadWriter[ToolChoice] = SnakePickle
    .readwriter[ujson.Value]
    .bimap[ToolChoice](
      {
        case mode: ToolChoiceMode  => SnakePickle.writeJs[ToolChoiceMode](mode)
        case obj: ToolChoiceObject => SnakePickle.writeJs[ToolChoiceObject](obj)
      },
      {
        case str: ujson.Str => SnakePickle.read[ToolChoiceMode](str)
        case obj: ujson.Obj => SnakePickle.read[ToolChoiceObject](obj)
        case v              => throw new Exception(s"Invalid tool choice format: $v")
      }
    )
}
