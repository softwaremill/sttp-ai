package sttp.openai

import sttp.client4.{DefaultSyncBackend, Request, SyncBackend}
import sttp.openai.OpenAIExceptions.OpenAIException
import sttp.openai.requests.audio.AudioResponseData.AudioResponse
import sttp.openai.requests.audio.RecognitionModel
import sttp.openai.requests.audio.transcriptions.TranscriptionConfig
import sttp.openai.requests.audio.translations.TranslationConfig
import sttp.openai.requests.completions.CompletionsRequestBody.CompletionsBody
import sttp.openai.requests.completions.CompletionsResponseData.CompletionsResponse
import sttp.openai.requests.completions.chat.ChatRequestBody.ChatBody
import sttp.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.openai.requests.completions.edit.EditRequestBody.EditBody
import sttp.openai.requests.completions.edit.EditRequestResponseData.EditResponse
import sttp.openai.requests.embeddings.EmbeddingsRequestBody.EmbeddingsBody
import sttp.openai.requests.embeddings.EmbeddingsResponseBody.EmbeddingResponse
import sttp.openai.requests.files.FilesResponseData.{DeletedFileData, FileData, FilesResponse}
import sttp.openai.requests.finetunes.FineTunesRequestBody
import sttp.openai.requests.finetunes.FineTunesResponseData.{
  DeleteFineTuneModelResponse,
  FineTuneEventsResponse,
  FineTuneResponse,
  GetFineTunesResponse
}
import sttp.openai.requests.images.ImageResponseData.ImageResponse
import sttp.openai.requests.images.creation.ImageCreationRequestBody.ImageCreationBody
import sttp.openai.requests.images.edit.ImageEditsConfig
import sttp.openai.requests.images.variations.ImageVariationsConfig
import sttp.openai.requests.models.ModelsResponseData.{ModelData, ModelsResponse}
import sttp.openai.requests.moderations.ModerationsRequestBody.ModerationsBody
import sttp.openai.requests.moderations.ModerationsResponseData.ModerationData

import java.io.File

class OpenAISyncClient private (authToken: String, backend: SyncBackend, closeClient: Boolean) {

  private val openAI = new OpenAI(authToken)

  /** Lists the currently available models, and provides basic information about each one such as the owner and availability.
    *
    * [[https://platform.openai.com/docs/api-reference/models]]
    */
  def getModels: ModelsResponse =
    sendOrThrow(openAI.getModels)

  /** Retrieves a model instance, providing basic information about the model such as the owner and permissions.
    *
    * [[https://platform.openai.com/docs/api-reference/models/retrieve]]
    *
    * @param modelId
    *   The ID of the model to use for this request.
    */
  def retrieveModel(modelId: String): ModelData =
    sendOrThrow(openAI.retrieveModel(modelId))

  /** Creates a completion for the provided prompt and parameters given in request body.
    *
    * [[https://platform.openai.com/docs/api-reference/completions/create]]
    *
    * @param completionBody
    *   Create completion request body.
    */
  def createCompletion(completionBody: CompletionsBody): CompletionsResponse =
    sendOrThrow(openAI.createCompletion(completionBody))

  /** Creates an image given a prompt in request body.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create]]
    *
    * @param imageCreationBody
    *   Create image request body.
    */
  def createImage(imageCreationBody: ImageCreationBody): ImageResponse =
    sendOrThrow(openAI.createImage(imageCreationBody))

  /** Creates edited or extended images given an original image and a prompt.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-edit]]
    *
    * @param image
    *   The image to be edited.
    *
    * Must be a valid PNG file, less than 4MB, and square. If mask is not provided, image must have transparency, which will be used as the
    * mask.
    * @param prompt
    *   A text description of the desired image. The maximum length is 1000 characters.
    */
  def imageEdits(image: File, prompt: String): ImageResponse =
    sendOrThrow(openAI.imageEdits(image, prompt))

  /** Creates edited or extended images given an original image and a prompt.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-edit]]
    *
    * @param systemPath
    *   Path to the image to be edited.
    *
    * Must be a valid PNG file, less than 4MB, and square. If mask is not provided, image must have transparency, which will be used as the
    * mask
    * @param prompt
    *   A text description of the desired image. The maximum length is 1000 characters.
    */
  def imageEdits(systemPath: String, prompt: String): ImageResponse =
    sendOrThrow(openAI.imageEdits(systemPath, prompt))

  /** Creates edited or extended images given an original image and a prompt.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-edit]]
    *
    * @param imageEditsConfig
    *   An instance of the case class ImageEditConfig containing the necessary parameters for editing the image.
    */
  def imageEdits(imageEditsConfig: ImageEditsConfig): ImageResponse =
    sendOrThrow(openAI.imageEdits(imageEditsConfig))

  /** Creates a variation of a given image.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-variation]]
    *
    * @param image
    *   The image to use as the basis for the variation.
    *
    * Must be a valid PNG file, less than 4MB, and square.
    */
  def imageVariations(image: File): ImageResponse =
    sendOrThrow(openAI.imageVariations(image))

  /** Creates a variation of a given image.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-variation]]
    *
    * @param systemPath
    *   Path to the image to use as the basis for the variation.
    *
    * Must be a valid PNG file, less than 4MB, and square.
    */
  def imageVariations(systemPath: String): ImageResponse =
    sendOrThrow(openAI.imageVariations(systemPath))

  /** Creates a variation of a given image.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-variation]]
    *
    * @param imageVariationsConfig
    *   An instance of the case class ImageVariationsConfig containing the necessary parameters for the image variation.
    */
  def imageVariations(imageVariationsConfig: ImageVariationsConfig): ImageResponse =
    sendOrThrow(openAI.imageVariations(imageVariationsConfig))

  /** Creates a new edit for provided request body.
    *
    * [[https://platform.openai.com/docs/api-reference/edits/create]]
    *
    * @param editRequestBody
    *   Edit request body.
    */
  def createEdit(editRequestBody: EditBody): EditResponse =
    sendOrThrow(openAI.createEdit(editRequestBody))

  /** Creates a model response for the given chat conversation defined in chatBody.
    *
    * [[https://platform.openai.com/docs/api-reference/chat/create]]
    *
    * @param chatBody
    *   Chat request body.
    */
  def createChatCompletion(chatBody: ChatBody): ChatResponse =
    sendOrThrow(openAI.createChatCompletion(chatBody))

  /** Returns a list of files that belong to the user's organization.
    *
    * [[https://platform.openai.com/docs/api-reference/files]]
    */
  def getFiles: FilesResponse =
    sendOrThrow(openAI.getFiles)

  /** Upload a file that contains document(s) to be used across various endpoints/features. Currently, the size of all the files uploaded by
    * one organization can be up to 1 GB. Please contact OpenAI if you need to increase the storage limit.
    *
    * [[https://platform.openai.com/docs/api-reference/files/upload]]
    *
    * @param file
    *   JSON Lines file to be uploaded.
    *
    * If the purpose is set to "fine-tune", each line is a JSON record with "prompt" and "completion" fields representing your
    * [[https://platform.openai.com/docs/guides/fine-tuning/prepare-training-data training examples]].
    * @param purpose
    *   The intended purpose of the uploaded documents.
    *
    * Use "fine-tune" for Fine-tuning. This allows OpenAI to validate the format of the uploaded file.
    */
  def uploadFile(file: File, purpose: String): FileData =
    sendOrThrow(openAI.uploadFile(file, purpose))

  /** Upload a file that contains document(s) to be used across various endpoints/features. Currently, the size of all the files uploaded by
    * one organization can be up to 1 GB. Please contact OpenAI if you need to increase the storage limit.
    *
    * [[https://platform.openai.com/docs/api-reference/files/upload]]
    *
    * @param file
    *   JSON Lines file to be uploaded and the purpose is set to "fine-tune", each line is a JSON record with "prompt" and "completion"
    *   fields representing your [[https://platform.openai.com/docs/guides/fine-tuning/prepare-training-data training examples]].
    */
  def uploadFile(file: File): FileData =
    sendOrThrow(openAI.uploadFile(file))

  /** Upload a file that contains document(s) to be used across various endpoints/features. Currently, the size of all the files uploaded by
    * one organization can be up to 1 GB. Please contact OpenAI if you need to increase the storage limit.
    *
    * [[https://platform.openai.com/docs/api-reference/files/upload]]
    *
    * @param systemPath
    *   Path to the JSON Lines file to be uploaded.
    *
    * If the purpose is set to "fine-tune", each line is a JSON record with "prompt" and "completion" fields representing your
    * [[https://platform.openai.com/docs/guides/fine-tuning/prepare-training-data training examples]].
    * @param purpose
    *   The intended purpose of the uploaded documents.
    *
    * Use "fine-tune" for Fine-tuning. This allows OpenAI to validate the format of the uploaded file.
    */
  def uploadFile(systemPath: String, purpose: String): FileData =
    sendOrThrow(openAI.uploadFile(systemPath, purpose))

  /** Upload a file that contains document(s) to be used across various endpoints/features. Currently, the size of all the files uploaded by
    * one organization can be up to 1 GB. Please contact OpenAI if you need to increase the storage limit.
    *
    * [[https://platform.openai.com/docs/api-reference/files/upload]]
    *
    * @param systemPath
    *   Path to the JSON Lines file to be uploaded and the purpose is set to "fine-tune", each line is a JSON record with "prompt" and
    *   "completion" fields representing your
    *   [[https://platform.openai.com/docs/guides/fine-tuning/prepare-training-data training examples]].
    */
  def uploadFile(systemPath: String): FileData =
    sendOrThrow(openAI.uploadFile(systemPath))

  /** Delete a file.
    *
    * [[https://platform.openai.com/docs/api-reference/files/delete]]
    *
    * @param fileId
    *   The ID of the file to use for this request.
    */
  def deleteFile(fileId: String): DeletedFileData =
    sendOrThrow(openAI.deleteFile(fileId))

  /** Returns information about a specific file.
    *
    * [[https://platform.openai.com/docs/api-reference/files/retrieve]]
    *
    * @param fileId
    *   The ID of the file to use for this request.
    */
  def retrieveFile(fileId: String): FileData =
    sendOrThrow(openAI.retrieveFile(fileId))

  /** Returns the contents of the specified file.
    *
    * [[https://platform.openai.com/docs/api-reference/files/retrieve-content]]
    *
    * @param fileId
    *   The ID of the file.
    */
  def retrieveFileContent(fileId: String): String =
    sendOrThrow(openAI.retrieveFileContent(fileId))

  /** Translates audio into English text.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param file
    *   The audio file to translate, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or webm.
    * @param model
    *   ID of the model to use. Only whisper-1 is currently available.
    */
  def createTranslation(file: File, model: RecognitionModel): AudioResponse =
    sendOrThrow(openAI.createTranslation(file, model))

  /** Translates audio into English text.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param systemPath
    *   The audio systemPath to transcribe, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or webm.
    * @param model
    *   ID of the model to use. Only whisper-1 is currently available.
    */
  def createTranslation(systemPath: String, model: RecognitionModel): AudioResponse =
    sendOrThrow(openAI.createTranslation(systemPath, model))

  /** Translates audio into English text.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param translationConfig
    *   An instance of the case class TranslationConfig containing the necessary parameters for the audio translation.
    */
  def createTranslation(translationConfig: TranslationConfig): AudioResponse =
    sendOrThrow(openAI.createTranslation(translationConfig))

  /** Classifies if text violates OpenAI's Content Policy.
    *
    * [[https://platform.openai.com/docs/api-reference/moderations/create]]
    *
    * @param moderationsBody
    *   Moderation request body.
    */
  def createModeration(moderationsBody: ModerationsBody): ModerationData =
    sendOrThrow(openAI.createModeration(moderationsBody))

  /** Transcribes audio into the input language.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param file
    *   The audio file to transcribe, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or webm.
    * @param model
    *   ID of the model to use. Only whisper-1 is currently available.
    */
  def createTranscription(file: File, model: RecognitionModel): AudioResponse =
    sendOrThrow(openAI.createTranscription(file, model))

  /** Transcribes audio into the input language.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param systemPath
    *   The audio systemPath to transcribe, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or webm.
    * @param model
    *   ID of the model to use. Only whisper-1 is currently available.
    */
  def createTranscription(systemPath: String, model: RecognitionModel): AudioResponse =
    sendOrThrow(openAI.createTranscription(systemPath, model))

  /** Transcribes audio into the input language.
    *
    * @param transcriptionConfig
    *   An instance of the case class TranscriptionConfig containing the necessary parameters for the audio transcription
    * @return
    *   An url to edited image.
    */
  def createTranscription(transcriptionConfig: TranscriptionConfig): AudioResponse =
    sendOrThrow(openAI.createTranscription(transcriptionConfig))

  /** Creates a job that fine-tunes a specified model from a given dataset.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tunes/create]]
    *
    * @param fineTunesRequestBody
    *   Request body that will be used to create a fine-tune.
    */
  def createFineTune(fineTunesRequestBody: FineTunesRequestBody): FineTuneResponse =
    sendOrThrow(openAI.createFineTune(fineTunesRequestBody))

  /** List of your organization's fine-tuning jobs.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tunes/list]]
    */
  def getFineTunes: GetFineTunesResponse =
    sendOrThrow(openAI.getFineTunes)

  /** Immediately cancel a fine-tune job.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tunes/cancel]]
    *
    * @param fineTuneId
    *   The ID of the fine-tune job to cancel.
    */
  def cancelFineTune(fineTuneId: String): FineTuneResponse =
    sendOrThrow(openAI.cancelFineTune(fineTuneId))

  /** Gets info about the fine-tune job.
    *
    * [[https://platform.openai.com/docs/api-reference/embeddings/create]]
    *
    * @param embeddingsBody
    *   Embeddings request body.
    */
  def createEmbeddings(embeddingsBody: EmbeddingsBody): EmbeddingResponse =
    sendOrThrow(openAI.createEmbeddings(embeddingsBody))

  /** Gets info about the fine-tune job.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tunes/retrieve]]
    *
    * @param fineTuneId
    *   The ID of the fine-tune job.
    */
  def retrieveFineTune(fineTuneId: String): FineTuneResponse =
    sendOrThrow(openAI.retrieveFineTune(fineTuneId))

  /** Delete a fine-tuned model. You must have the Owner role in your organization.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tunes/delete-model]]
    *
    * @param model
    *   The model to delete.
    */
  def deleteFineTuneModel(model: String): DeleteFineTuneModelResponse =
    sendOrThrow(openAI.deleteFineTuneModel(model))

  /** Get fine-grained status updates for a fine-tune job.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tunes/events]]
    *
    * @param fineTuneId
    *   The ID of the fine-tune job to get events for.
    */
  def getFineTuneEvents(fineTuneId: String): FineTuneEventsResponse =
    sendOrThrow(openAI.getFineTuneEvents(fineTuneId))

  /** Closes and releases resources of http client if was not provided explicitly, otherwise works no-op.
    */
  def close(): Unit = if (closeClient) backend.close() else ()

  private def sendOrThrow[A](request: Request[Either[OpenAIException, A]]): A =
    request.send(backend).body match {
      case Right(value)    => value
      case Left(exception) => throw exception
    }
}

object OpenAISyncClient {
  def apply(authToken: String) = new OpenAISyncClient(authToken, DefaultSyncBackend(), true)
  def apply(authToken: String, backend: SyncBackend) = new OpenAISyncClient(authToken, backend, false)
}
