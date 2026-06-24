package sttp.ai.openai.requests.threads.messages

import sttp.ai.openai.requests.completions.chat.message.Attachment

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

  case class DeleteMessageResponse(
      `object`: String = "thread.message.deleted",
      id: String,
      deleted: Boolean
  )

  sealed trait Annotation

  object Annotation {

    /** @param fileId
      *   The ID of the specific File the citation is from.
      * @param quote
      *   The specific quote in the file.
      */
    case class FileCitationDetails(
        fileId: String,
        quote: String
    )

    /** A citation within the message that points to a specific quote from a specific File associated with the assistant or the message.
      * Generated when the assistant uses the "file_search" tool to search files.
      *
      * @param text
      *   The text in the message content that needs to be replaced.
      * @param fileCitation
      * @param startIndex
      * @param endIndex
      */
    case class FileCitation(
        text: String,
        fileCitation: FileCitationDetails,
        startIndex: Int,
        endIndex: Int
    ) extends Annotation

    /** @param fileId
      *   The ID of the file that was generated.
      */
    case class FilePathDetails(fileId: String)

    /** URL for the file that's generated when the assistant used the code_interpreter tool to generate a file.
      *
      * @param text
      *   The text in the message content that needs to be replaced.
      * @param filePath
      * @param startIndex
      * @param endIndex
      */
    case class FilePath(
        text: String,
        filePath: FilePathDetails,
        startIndex: Int,
        endIndex: Int
    ) extends Annotation
  }
  sealed trait Content

  object Content {

    /** @param value
      *   The data that makes up the text
      * @param annotations.
      */
    case class TextContentValue(value: String, annotations: Seq[Annotation])

    /** The text content that is part of a message
      */
    case class Text(text: TextContentValue) extends Content

    /** @param fileId
      *   string The File ID of the image in the message content.
      */
    case class ImageFileDetails(fileId: String)

    /** References an image File in the content of a message
      *
      * @param type
      *   Always image_file.
      *
      * @param imageFile
      *   object
      */
    case class ImageFile(imageFile: ImageFileDetails) extends Content
  }

}
