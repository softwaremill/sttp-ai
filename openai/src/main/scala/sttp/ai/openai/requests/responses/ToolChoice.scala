package sttp.ai.openai.requests.responses

sealed trait ToolChoice

object ToolChoice {
  sealed trait ToolChoiceMode extends ToolChoice
  object ToolChoiceMode {
    case object None extends ToolChoiceMode
    case object Auto extends ToolChoiceMode
    case object Required extends ToolChoiceMode
  }

  sealed trait ToolChoiceObject extends ToolChoice
  object ToolChoiceObject {
    object AllowedTools {
      sealed trait ToolDefinition
      object ToolDefinition {
        case class Function(name: String) extends ToolDefinition
        case class Mcp(serverLabel: String) extends ToolDefinition
        case class ImageGeneration() extends ToolDefinition
      }
    }

    case class AllowedTools(mode: String, tools: List[AllowedTools.ToolDefinition]) extends ToolChoiceObject
    case class FileSearch() extends ToolChoiceObject
    case class WebSearchPreview() extends ToolChoiceObject
    case class ComputerUsePreview() extends ToolChoiceObject
    case class CodeInterpreter() extends ToolChoiceObject
    case class ImageGeneration() extends ToolChoiceObject
    case class Function(name: String) extends ToolChoiceObject
    case class Mcp(serverLabel: String, name: Option[String] = None) extends ToolChoiceObject
    case class Custom(name: String) extends ToolChoiceObject
  }
}
