package sttp.ai.openai.requests.vectorstore.file

sealed trait FileStatus
case object InProgress extends FileStatus
case object Completed extends FileStatus
case object Failed extends FileStatus
case object Cancelled extends FileStatus
