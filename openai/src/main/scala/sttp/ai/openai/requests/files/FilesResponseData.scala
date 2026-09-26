package sttp.ai.openai.requests.files

import scala.annotation.nowarn

object FilesResponseData {
  // Scala 2 reports the deprecated fields through the synthetic apply/copy/unapply
  @nowarn("cat=deprecation")
  case class FileData(
      `object`: String,
      id: String,
      purpose: String,
      filename: String,
      bytes: Int,
      createdAt: Int,
      @deprecated("Mark as deprecated in OpenAI spec", "0.2.0") status: String,
      @deprecated("Mark as deprecated in OpenAI spec", "0.2.0") statusDetails: Option[String]
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
