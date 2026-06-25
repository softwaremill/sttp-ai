package sttp.ai.openai.requests.files

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

  case class FilesResponse(
      `object`: String,
      data: Seq[FileData]
  )

  case class DeletedFileData(
      `object`: String,
      id: String,
      deleted: Boolean
  )
}
