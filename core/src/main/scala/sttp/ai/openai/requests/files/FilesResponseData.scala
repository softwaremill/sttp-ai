package sttp.openai.requests.files

import sttp.openai.json.SnakePickle

object FilesResponseData {
  case class FileData(
      `object`: String,
      id: String,
      purpose: String,
      filename: String,
      bytes: Int,
      createdAt: Int,
      @deprecated("Mark as deprecated in OpenAI spec") status: String,
      @deprecated("Mark as deprecated in OpenAI spec") statusDetails: Option[String]
  )

  object FileData {
    implicit val fileInfoReadWriter: SnakePickle.ReadWriter[FileData] = SnakePickle.macroRW[FileData]
  }

  case class FilesResponse(
      `object`: String,
      data: Seq[FileData]
  )

  object FilesResponse {
    implicit val filesResponseReadWriter: SnakePickle.ReadWriter[FilesResponse] = SnakePickle.macroRW[FilesResponse]
  }

  case class DeletedFileData(
      `object`: String,
      id: String,
      deleted: Boolean
  )

  object DeletedFileData {
    implicit val deleteFileResponseReadWriter: SnakePickle.ReadWriter[DeletedFileData] = SnakePickle.macroRW[DeletedFileData]
  }
}
