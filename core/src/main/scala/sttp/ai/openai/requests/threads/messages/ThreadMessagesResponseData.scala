package sttp.openai.requests.threads.messages

import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.chat.message.Attachment

object ThreadMessagesResponseData {

  /** @param id
    *   The identifier, which can be referenced in API endpoints.
    * @param object
    *   The object type, which is always thread.message.
    * @param createdAt
    *   The Unix timestamp (in seconds) for when the message was created.
    * @param threadId
    *   The thread ID that this message belongs to.
    * @param role
    *   The entity that produced the message. One of user or assistant.
    * @param content
    *   The content of the message in array of text and/or images.
    * @param assistantId
    *   If applicable, the ID of the assistant that authored this message.
    * @param runId
    *   If applicable, the ID of the run associated with the authoring of this message.
    * @param attachments
    *   A list of files attached to the message, and the tools they were added to.
    * @param metadata
    *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object
    *   in a structured format. Keys can be a maximum of 64 characters long and values can be a maxium of 512 characters long.
    *
    * For more information please visit: [[https://platform.openai.com/docs/api-reference/messages/object]]
    */
  case class MessageData(
      id: String,
      `object`: String = "thread.message",
      createdAt: Int,
      threadId: Option[String] = None,
      role: String,
      content: Seq[Content],
      assistantId: Option[String] = None,
      runId: Option[String] = None,
      attachments: Option[Seq[Attachment]] = None,
      metadata: Map[String, String] = Map.empty
  )

  object MessageData {
    implicit val messageDataR: SnakePickle.Reader[MessageData] = SnakePickle.macroR[MessageData]
  }

  /** @param object
    *   Always "list"
    * @param data
    *   A list of message objects.
    * @param firstId
    * @param lastId
    * @param hasMore
    *   }
    */
  case class ListMessagesResponse(
      `object`: String = "list",
      data: Seq[MessageData],
      firstId: String,
      lastId: String,
      hasMore: Boolean
  )
  object ListMessagesResponse {
    implicit val listMessagesResponseR: SnakePickle.Reader[ListMessagesResponse] = SnakePickle.macroR[ListMessagesResponse]
  }
  case class DeleteMessageResponse(
      `object`: String = "thread.message.deleted",
      id: String,
      deleted: Boolean
  )
  object DeleteMessageResponse {
    implicit val deleteMessageResponseR: SnakePickle.Reader[DeleteMessageResponse] = SnakePickle.macroR[DeleteMessageResponse]
  }

  sealed trait Annotation

  /** @param fileId
    *   The ID of the specific File the citation is from.
    *
    * @param quote
    *   The specific quote in the file.
    */
  case class FileCitation(
      fileId: String,
      quote: String
  )

  implicit val fileCitationR: SnakePickle.Reader[FileCitation] = SnakePickle.macroR[FileCitation]

  /** A citation within the message that points to a specific quote from a specific File associated with the assistant or the message.
    * Generated when the assistant uses the "file_search" tool to search files.
    * @param type
    *   Always file_citation.
    *
    * @param text
    *   The text in the message content that needs to be replaced.
    *
    * @param fileCitation
    * @param startIndex
    * @param endIndex
    */
  case class FileCitationAnnotation(
      `type`: String,
      text: String,
      fileCitation: FileCitation,
      startIndex: Int,
      endIndex: Int
  ) extends Annotation

  implicit val fileCitationAnnotationR: SnakePickle.Reader[FileCitationAnnotation] = SnakePickle.macroR[FileCitationAnnotation]

  /** @param fileId
    *   The ID of the file that was generated.
    */
  case class FilePath(fileId: String)

  implicit val filePathR: SnakePickle.Reader[FilePath] = SnakePickle.macroR[FilePath]

  /** URL for the file that's generated when the assistant used the code_interpreter tool to generate a file.
    * @param type
    *   Always "file_path".
    *
    * @param text
    *   The text in the message content that needs to be replaced.
    *
    * @param filePath
    * @param startIndex
    * @param endIndex
    */
  case class FilePathAnnotation(
      `type`: String,
      text: String,
      filePath: FilePath,
      startIndex: Int,
      endIndex: Int
  ) extends Annotation

  implicit val filePathAnnotationR: SnakePickle.Reader[FilePathAnnotation] = SnakePickle.macroR[FilePathAnnotation]

  implicit val annotationR: SnakePickle.Reader[Annotation] = SnakePickle
    .reader[ujson.Value]
    .map(json =>
      json("type").str match {
        case "file_citation" => SnakePickle.read[FileCitationAnnotation](json)
        case "file_path"     => SnakePickle.read[FilePathAnnotation](json)
      }
    )

  // should be sealed trait but there are problems with $type fields
  trait Content

  object Content {

    /** @param value
      *   The data that makes up the text
      * @param annotations.
      */
    case class TextContentValue(value: String, annotations: Seq[Annotation])

    implicit val textContentValueR: SnakePickle.Reader[TextContentValue] = SnakePickle.macroR[TextContentValue]

    /** The text content that is part of a message
      * @param `type`
      *   Always text.
      */
    case class TextContent(`type`: String, text: TextContentValue) extends Content

    implicit val textContentR: SnakePickle.Reader[TextContent] = SnakePickle.macroR[TextContent]

    /** @param fileId
      *   string The File ID of the image in the message content.
      */
    case class ImageFile(fileId: String)

    implicit val imageFileR: SnakePickle.Reader[ImageFile] = SnakePickle.macroR[ImageFile]

    /** References an image File in the content of a message
      *
      * @param type
      *   Always image_file.
      *
      * @param imageFile
      *   object
      */
    case class ImageFileContent(`type`: String, imageFile: ImageFile) extends Content

    implicit val imageFileContentR: SnakePickle.Reader[ImageFileContent] = SnakePickle.macroR[ImageFileContent]

    implicit val contentR: SnakePickle.Reader[Content] = SnakePickle
      .reader[ujson.Value]
      .map(json =>
        json("type").str match {
          case "text" =>
            SnakePickle.read[TextContent](json)
          case "image_file" => SnakePickle.read[ImageFileContent](json)
        }
      )
  }

}
