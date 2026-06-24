package sttp.ai.openai.requests.completions.chat.message

sealed trait ToolChoice

object ToolChoice {

  /** Means that the model will not call a function and instead generates a message. */
  case object None extends ToolChoice

  /** Means the model can pick between generating a message or calling a function. */
  case object Auto extends ToolChoice
  case object Required extends ToolChoice

  /** Means the model will call a specific function. */
  case class Function(name: String) extends ToolChoice

  /** Means the model will call a specific custom tool. */
  case class Custom(name: String) extends ToolChoice

  case class AllowedTools(mode: AllowedTools.Mode, tools: List[Tool]) extends ToolChoice
  object AllowedTools {
    sealed trait Mode
    object Mode {
      case object Auto extends Mode

      case object Required extends Mode
    }
  }
}
