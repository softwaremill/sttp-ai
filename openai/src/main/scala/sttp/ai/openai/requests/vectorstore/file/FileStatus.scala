package sttp.ai.openai.requests.vectorstore.file

sealed trait FileStatus
case object InProgress extends FileStatus
case object Completed extends FileStatus
case object Failed extends FileStatus
case object Cancelled extends FileStatus

object FileStatus {

  /** The snake_case value used by the API (e.g. as the `filter` query parameter). */
  def toApiValue(status: FileStatus): String = status match {
    case InProgress => "in_progress"
    case Completed  => "completed"
    case Failed     => "failed"
    case Cancelled  => "cancelled"
  }
}
