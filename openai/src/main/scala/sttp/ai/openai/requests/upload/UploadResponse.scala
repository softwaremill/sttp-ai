package sttp.ai.openai.requests.upload

/** Represents the response for an upload request.
  *
  * @param id
  *   The Upload unique identifier, which can be referenced in API endpoints.
  * @param `object`
  *   The object type, which is always "upload".
  * @param bytes
  *   The intended number of bytes to be uploaded.
  * @param createdAt
  *   The Unix timestamp (in seconds) for when the Upload was created.
  * @param filename
  *   The name of the file to be uploaded.
  * @param purpose
  *   The intended purpose of the file. Please refer here for acceptable values.
  * @param status
  *   The status of the Upload.
  * @param expiresAt
  *   The Unix timestamp (in seconds) for when the Upload will expire.
  * @param file
  *   The File object represents a document that has been uploaded to OpenAI.
  */
case class UploadResponse(
    id: String,
    `object`: String = "upload",
    bytes: Int,
    createdAt: Int,
    filename: String,
    purpose: String,
    status: String,
    expiresAt: Int,
    file: Option[FileMetadata]
)

case class FileMetadata(
    id: String,
    `object`: String,
    bytes: Int,
    createdAt: Int,
    filename: String,
    purpose: String
)

/** Represents the response for an upload part.
  *
  * @param id
  *   The upload Part unique identifier, which can be referenced in API endpoints.
  * @param createdAt
  *   The Unix timestamp (in seconds) for when the Part was created.
  * @param uploadId
  *   The ID of the Upload object that this Part was added to.
  * @param `object`
  *   The object type, which is always upload.part.
  */
case class UploadPartResponse(
    id: String,
    createdAt: Int,
    uploadId: String,
    `object`: String = "upload.part"
)
