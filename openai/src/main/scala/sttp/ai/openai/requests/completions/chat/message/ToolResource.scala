package sttp.ai.openai.requests.completions.chat.message

sealed trait ToolResource

object ToolResource {

  /** Code interpreter tool
    *
    * The type of tool being defined: code_interpreter
    */
  case class CodeInterpreterToolResource(fileIds: Option[Seq[String]] = None) extends ToolResource

  /** file_search tool
    *
    * The type of tool being defined: file_search
    */
  case class FileSearchToolResource(vectorStoreIds: Option[Seq[String]] = None, vectorStores: Option[Seq[String]] = None)
      extends ToolResource
}
