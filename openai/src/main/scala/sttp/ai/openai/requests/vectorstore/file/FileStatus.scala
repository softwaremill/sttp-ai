package sttp.ai.openai.requests.vectorstore.file

import sttp.ai.core.json.SnakePickle
import ujson.Value

sealed trait FileStatus
case object InProgress extends FileStatus
case object Completed extends FileStatus
case object Failed extends FileStatus
case object Cancelled extends FileStatus

object FileStatus {
  implicit val expiresAfterRW: SnakePickle.ReadWriter[FileStatus] = SnakePickle
    .readwriter[Value]
    .bimap[FileStatus](
      {
        case InProgress => "in_progress"
        case Completed  => "completed"
        case Failed     => "failed"
        case Cancelled  => "cancelled"
      },
      json =>
        json.str match {
          case "in_progress" => InProgress
          case "completed"   => Completed
          case "failed"      => Failed
          case "cancelled"   => Cancelled
        }
    )
}
