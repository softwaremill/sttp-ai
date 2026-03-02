package sttp.ai.openai

import sttp.ai.openai.OpenAIExceptions.OpenAIException
import sttp.ai.openai.config.OpenAIConfig
import sttp.ai.openai.requests.admin.{AdminApiKeyRequestBody, AdminApiKeyResponse, DeleteAdminApiKeyResponse, ListAdminApiKeyResponse, QueryParameters => _}
import sttp.ai.openai.requests.assistants.AssistantsRequestBody.{CreateAssistantBody, ModifyAssistantBody}
import sttp.ai.openai.requests.assistants.AssistantsResponseData.{AssistantData, DeleteAssistantResponse, ListAssistantsResponse}
import sttp.ai.openai.requests.audio.AudioResponseData.AudioResponse
import sttp.ai.openai.requests.audio.transcriptions.{TranscriptionConfig, TranscriptionModel}
import sttp.ai.openai.requests.audio.translations.{TranslationConfig, TranslationModel}
import sttp.ai.openai.requests.batch.{BatchRequestBody, BatchResponse, ListBatchResponse}
import sttp.ai.openai.requests.completions.CompletionsRequestBody.CompletionsBody
import sttp.ai.openai.requests.completions.CompletionsResponseData.CompletionsResponse
import sttp.ai.openai.requests.completions.chat
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, UpdateChatCompletionRequestBody}
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.{ChatResponse, DeleteChatCompletionResponse, ListChatResponse, ListMessageResponse}
import sttp.ai.openai.requests.completions.chat.{ListMessagesQueryParameters => _}
import sttp.ai.openai.requests.embeddings.EmbeddingsRequestBody.EmbeddingsBody
import sttp.ai.openai.requests.embeddings.EmbeddingsResponseBody.EmbeddingResponse
import sttp.ai.openai.requests.files.FilesResponseData.{DeletedFileData, FileData, FilesResponse}
import sttp.ai.openai.requests.finetuning.{FineTuningJobRequestBody, FineTuningJobResponse, ListFineTuningJobCheckpointResponse, ListFineTuningJobEventResponse, ListFineTuningJobResponse}
import sttp.ai.openai.requests.images.ImageResponseData.ImageResponse
import sttp.ai.openai.requests.images.creation.ImageCreationRequestBody.ImageCreationBody
import sttp.ai.openai.requests.images.edit.ImageEditsConfig
import sttp.ai.openai.requests.images.variations.ImageVariationsConfig
import sttp.ai.openai.requests.models.ModelsResponseData.{DeletedModelData, ModelData, ModelsResponse}
import sttp.ai.openai.requests.moderations.ModerationsRequestBody.ModerationsBody
import sttp.ai.openai.requests.moderations.ModerationsResponseData.ModerationData
import sttp.ai.openai.requests.responses.{DeleteModelResponseResponse, GetResponseQueryParameters, InputItemsListResponseBody, ListInputItemsQueryParameters, ResponsesRequestBody, ResponsesResponseBody}
import sttp.ai.openai.requests.threads.QueryParameters
import sttp.ai.openai.requests.threads.ThreadsRequestBody.CreateThreadBody
import sttp.ai.openai.requests.threads.ThreadsResponseData.{DeleteThreadResponse, ThreadData}
import sttp.ai.openai.requests.threads.messages.ThreadMessagesRequestBody.CreateMessage
import sttp.ai.openai.requests.threads.messages.ThreadMessagesResponseData.{DeleteMessageResponse, ListMessagesResponse, MessageData}
import sttp.ai.openai.requests.threads.runs.ThreadRunsRequestBody.{CreateRun, CreateThreadAndRun, ToolOutput}
import sttp.ai.openai.requests.threads.runs.ThreadRunsResponseData.{ListRunStepsResponse, ListRunsResponse, RunData, RunStepData}
import sttp.ai.openai.requests.upload.{CompleteUploadRequestBody, UploadPartResponse, UploadRequestBody, UploadResponse}
import sttp.ai.openai.requests.vectorstore.VectorStoreRequestBody.{CreateVectorStoreBody, ModifyVectorStoreBody}
import sttp.ai.openai.requests.vectorstore.VectorStoreResponseData.{DeleteVectorStoreResponse, ListVectorStoresResponse, VectorStore}
import sttp.ai.openai.requests.vectorstore.file.VectorStoreFileRequestBody.{CreateVectorStoreFileBody, ListVectorStoreFilesBody}
import sttp.ai.openai.requests.vectorstore.file.VectorStoreFileResponseData.{DeleteVectorStoreFileResponse, ListVectorStoreFilesResponse, VectorStoreFile}
import sttp.ai.openai.requests.{admin, batch, finetuning}
import sttp.capabilities.zio.ZioStreams
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.client4.{Request, WebSocketStreamBackend}
import sttp.model.Uri
import zio.{IO, Task, Unsafe, ZIO, ZLayer}
import sttp.ai.openai.OpenAIZioClient.OpenAiResponse

import java.io.File

class OpenAIZioClient private(
                               openAI: OpenAI,
                               backend: WebSocketStreamBackend[Task, ZioStreams]
                             ) {

  // private val openAI = new OpenAI(authToken, baseUri, organization)

  /** Lists the currently available models, and provides basic information about each one such as the owner and availability.
    *
    * [[https://platform.openai.com/docs/api-reference/models]]
    */
  def getModels: OpenAiResponse[ModelsResponse] =
    send(openAI.getModels)

  /** Retrieves a model instance, providing basic information about the model such as the owner and permissions.
    *
    * [[https://platform.openai.com/docs/api-reference/models/retrieve]]
    *
    * @param modelId
    *   The ID of the model to use for this request.
    */
  def retrieveModel(modelId: String): OpenAiResponse[ModelData] =
    send(openAI.retrieveModel(modelId))

  /** Delete a fine-tuned model. You must have the Owner role in your organization to delete a model.
    *
    * [[https://platform.openai.com/docs/api-reference/models/delete]]
    *
    * @param modelId
    *   The model to delete
    *
    * @return
    *   Deletion status.
    */
  def deleteModel(modelId: String): OpenAiResponse[DeletedModelData] =
    send(openAI.deleteModel(modelId))

  /** Creates a completion for the provided prompt and parameters given in request body.
    *
    * [[https://platform.openai.com/docs/api-reference/completions/create]]
    *
    * @param completionBody
    *   Create completion request body.
    * @deprecated
    *   This is marked as Legacy in OpenAI API and might be removed in the future. Please use [[createChatCompletion]] instead.
    */
  def createCompletion(completionBody: CompletionsBody): OpenAiResponse[CompletionsResponse] =
    send(openAI.createCompletion(completionBody))

  /** Creates an image given a prompt in request body.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create]]
    *
    * @param imageCreationBody
    *   Create image request body.
    */
  def createImage(imageCreationBody: ImageCreationBody): OpenAiResponse[ImageResponse] =
    send(openAI.createImage(imageCreationBody))

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
  def imageEdits(image: File, prompt: String): OpenAiResponse[ImageResponse] =
    send(openAI.imageEdits(image, prompt))

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
  def imageEdits(systemPath: String, prompt: String): OpenAiResponse[ImageResponse] =
    send(openAI.imageEdits(systemPath, prompt))

  /** Creates edited or extended images given an original image and a prompt.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-edit]]
    *
    * @param imageEditsConfig
    *   An instance of the case class ImageEditConfig containing the necessary parameters for editing the image.
    */
  def imageEdits(imageEditsConfig: ImageEditsConfig): OpenAiResponse[ImageResponse] =
    send(openAI.imageEdits(imageEditsConfig))

  /** Creates a variation of a given image.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-variation]]
    *
    * @param image
    *   The image to use as the basis for the variation.
    *
    * Must be a valid PNG file, less than 4MB, and square.
    */
  def imageVariations(image: File): OpenAiResponse[ImageResponse] =
    send(openAI.imageVariations(image))

  /** Creates a variation of a given image.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-variation]]
    *
    * @param systemPath
    *   Path to the image to use as the basis for the variation.
    *
    * Must be a valid PNG file, less than 4MB, and square.
    */
  def imageVariations(systemPath: String): OpenAiResponse[ImageResponse] =
    send(openAI.imageVariations(systemPath))

  /** Creates a variation of a given image.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-variation]]
    *
    * @param imageVariationsConfig
    *   An instance of the case class ImageVariationsConfig containing the necessary parameters for the image variation.
    */
  def imageVariations(imageVariationsConfig: ImageVariationsConfig): OpenAiResponse[ImageResponse] =
    send(openAI.imageVariations(imageVariationsConfig))

  /** Creates a model response for the given chat conversation defined in chatBody.
    *
    * [[https://platform.openai.com/docs/api-reference/chat/create]]
    *
    * @param chatBody
    *   Chat request body.
    */
  def createChatCompletion(chatBody: ChatBody): OpenAiResponse[ChatResponse] =
    send(openAI.createChatCompletion(chatBody))

  /** Get a stored chat completion. Only chat completions that have been created with the store parameter set to true will be returned.
    *
    * [[https://platform.openai.com/docs/api-reference/chat/get]]
    *
    * @param completionId
    *   The ID of the chat completion to retrieve.
    *
    * @return
    *   The ChatCompletion object matching the specified ID.
    */
  def getChatCompletion(completionId: String): OpenAiResponse[ChatResponse] =
    send(openAI.getChatCompletion(completionId))

  /** Get the messages in a stored chat completion. Only chat completions that have been created with the store parameter set to true will
    * be returned.
    *
    * [[https://platform.openai.com/docs/api-reference/chat/getMessages]]
    *
    * @param completionId
    *   The ID of the chat completion to retrieve messages from.
    *
    * @return
    *   A list of messages for the specified chat completion.
    */
  def getChatMessages(
      completionId: String,
      queryParameters: chat.ListMessagesQueryParameters = chat.ListMessagesQueryParameters.empty
  ): OpenAiResponse[ListMessageResponse] =
    send(openAI.getChatMessages(completionId, queryParameters))

  /** List stored chat completions. Only chat completions that have been stored with the store parameter set to true will be returned.
    *
    * [[https://platform.openai.com/docs/api-reference/chat/list]]
    *
    * @return
    *   A list of chat completions matching the specified filters.
    */
  def listChatCompletions(
      queryParameters: chat.ListChatCompletionsQueryParameters = chat.ListChatCompletionsQueryParameters.empty
  ): OpenAiResponse[ListChatResponse] =
    send(openAI.listChatCompletions(queryParameters))

  /** Modify a stored chat completion. Only chat completions that have been created with the store parameter set to true can be modified.
    * Currently, the only supported modification is to update the metadata field.
    *
    * [[https://platform.openai.com/docs/api-reference/chat/update]]
    *
    * @param completionId
    *   The ID of the chat completion to update.
    * @param requestBody
    *   Chat completion update request body.
    *
    * @return
    *   The ChatCompletion object matching the specified ID.
    */
  def updateChatCompletion(
      completionId: String,
      requestBody: UpdateChatCompletionRequestBody
  ): OpenAiResponse[ChatResponse] =
    send(openAI.updateChatCompletion(completionId, requestBody))

  /** Delete a stored chat completion. Only chat completions that have been created with the store parameter set to true can be deleted.
    *
    * [[https://platform.openai.com/docs/api-reference/chat/delete]]
    *
    * @param completionId
    *   The ID of the chat completion to delete.
    *
    * @return
    *   A deletion confirmation object.
    */
  def deleteChatCompletion(completionId: String): OpenAiResponse[DeleteChatCompletionResponse] =
    send(openAI.deleteChatCompletion(completionId))

  /** Creates a model response.
    *
    * Provide text or image inputs to generate text or JSON outputs. Have the model call your own custom code or use built-in tools like web
    * search or file search to use your own data as input for the model's response.
    *
    * [[https://platform.openai.com/docs/api-reference/responses/create]]
    *
    * @param requestBody
    *   Model response request body.
    *
    * @return
    *   Returns a Response object.
    */
  def createModelResponse(requestBody: ResponsesRequestBody): OpenAiResponse[ResponsesResponseBody] =
    send(openAI.createModelResponse(requestBody))

  /** Retrieves a model response with the given ID.
    *
    * [[https://platform.openai.com/docs/api-reference/responses/get]]
    *
    * @param responseId
    *   The ID of the response to retrieve.
    *
    * @return
    *   The Response object matching the specified ID.
    */
  def getModelResponse(
      responseId: String,
      queryParameters: GetResponseQueryParameters
  ): OpenAiResponse[ResponsesResponseBody] =
    send(openAI.getModelResponse(responseId, queryParameters))

  /** Deletes a model response with the given ID.
    *
    * [[https://platform.openai.com/docs/api-reference/responses/delete]]
    *
    * @param responseId
    *   The ID of the chat completion to delete.
    *
    * @return
    *   A deletion confirmation object.
    */
  def deleteModelResponse(responseId: String): OpenAiResponse[DeleteModelResponseResponse] =
    send(openAI.deleteModelResponse(responseId))

  /** Cancels a model response with the given ID.
    *
    * Only responses created with the background parameter set to true can be cancelled
    *
    * [[https://platform.openai.com/docs/api-reference/responses/cancel]]
    *
    * @param responseId
    *   The ID of the Upload.
    *
    * @return
    *   The Upload object with status cancelled.
    */
  def cancelResponse(responseId: String): OpenAiResponse[ResponsesResponseBody] =
    send(openAI.cancelResponse(responseId))

  /** Returns a list of input items for a given response.
    *
    * [[https://platform.openai.com/docs/api-reference/responses/input-items]]
    *
    * @param responseId
    *   The ID of the response to retrieve input items for.
    * @param queryParameters
    *   Query parameters for pagination and filtering.
    *
    * @return
    *   A list of input items for the response.
    */
  def listResponsesInputItems(
      responseId: String,
      queryParameters: ListInputItemsQueryParameters = ListInputItemsQueryParameters.empty
  ): OpenAiResponse[InputItemsListResponseBody] =
    send(openAI.listResponsesInputItems(responseId, queryParameters))

  /** Returns a list of files that belong to the user's organization.
    *
    * [[https://platform.openai.com/docs/api-reference/files]]
    */
  def getFiles: OpenAiResponse[FilesResponse] =
    send(openAI.getFiles)

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
  def uploadFile(file: File, purpose: String): OpenAiResponse[FileData] =
    send(openAI.uploadFile(file, purpose))

  /** Upload a file that contains document(s) to be used across various endpoints/features. Currently, the size of all the files uploaded by
    * one organization can be up to 1 GB. Please contact OpenAI if you need to increase the storage limit.
    *
    * [[https://platform.openai.com/docs/api-reference/files/upload]]
    *
    * @param file
    *   JSON Lines file to be uploaded and the purpose is set to "fine-tune", each line is a JSON record with "prompt" and "completion"
    *   fields representing your [[https://platform.openai.com/docs/guides/fine-tuning/prepare-training-data training examples]].
    */
  def uploadFile(file: File): OpenAiResponse[FileData] =
    send(openAI.uploadFile(file))

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
  def uploadFile(systemPath: String, purpose: String): OpenAiResponse[FileData] =
    send(openAI.uploadFile(systemPath, purpose))

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
  def uploadFile(systemPath: String): OpenAiResponse[FileData] =
    send(openAI.uploadFile(systemPath))

  /** Delete a file.
    *
    * [[https://platform.openai.com/docs/api-reference/files/delete]]
    *
    * @param fileId
    *   The ID of the file to use for this request.
    */
  def deleteFile(fileId: String): OpenAiResponse[DeletedFileData] =
    send(openAI.deleteFile(fileId))

  /** Returns information about a specific file.
    *
    * [[https://platform.openai.com/docs/api-reference/files/retrieve]]
    *
    * @param fileId
    *   The ID of the file to use for this request.
    */
  def retrieveFile(fileId: String): OpenAiResponse[FileData] =
    send(openAI.retrieveFile(fileId))

  /** Returns the contents of the specified file.
    *
    * [[https://platform.openai.com/docs/api-reference/files/retrieve-content]]
    *
    * @param fileId
    *   The ID of the file.
    */
  def retrieveFileContent(fileId: String): OpenAiResponse[String] =
    send(openAI.retrieveFileContent(fileId))

  /** Translates audio into English text.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param file
    *   The audio file to translate, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or webm.
    * @param model
    *   ID of the model to use. Only whisper-1 is currently available.
    */
  def createTranslation(file: File, model: TranslationModel): OpenAiResponse[AudioResponse] =
    send(openAI.createTranslation(file, model))

  /** Translates audio into English text.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param systemPath
    *   The audio systemPath to transcribe, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or webm.
    * @param model
    *   ID of the model to use. Only whisper-1 is currently available.
    */
  def createTranslation(systemPath: String, model: TranslationModel): OpenAiResponse[AudioResponse] =
    send(openAI.createTranslation(systemPath, model))

  /** Translates audio into English text.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param translationConfig
    *   An instance of the case class TranslationConfig containing the necessary parameters for the audio translation.
    */
  def createTranslation(translationConfig: TranslationConfig): OpenAiResponse[AudioResponse] =
    send(openAI.createTranslation(translationConfig))

  /** Classifies if text violates OpenAI's Content Policy.
    *
    * [[https://platform.openai.com/docs/api-reference/moderations/create]]
    *
    * @param moderationsBody
    *   Moderation request body.
    */
  def createModeration(moderationsBody: ModerationsBody): OpenAiResponse[ModerationData] =
    send(openAI.createModeration(moderationsBody))

  /** Transcribes audio into the input language.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param file
    *   The audio file to transcribe, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or webm.
    * @param model
    *   ID of the model to use. Whisper-1, gpt-4o-transcribe, gpt-4o-mini-transcribe are currently available.
    */
  def createTranscription(file: File, model: TranscriptionModel): OpenAiResponse[AudioResponse] =
    send(openAI.createTranscription(file, model))

  /** Transcribes audio into the input language.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param systemPath
    *   The audio systemPath to transcribe, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or webm.
    * @param model
    *   ID of the model to use. Whisper-1, gpt-4o-transcribe, gpt-4o-mini-transcribe are currently available.
    */
  def createTranscription(systemPath: String, model: TranscriptionModel): OpenAiResponse[AudioResponse] =
    send(openAI.createTranscription(systemPath, model))

  /** Transcribes audio into the input language.
    *
    * @param transcriptionConfig
    *   An instance of the case class TranscriptionConfig containing the necessary parameters for the audio transcription
    * @return
    *   An url to edited image.
    */
  def createTranscription(transcriptionConfig: TranscriptionConfig): OpenAiResponse[AudioResponse] =
    send(openAI.createTranscription(transcriptionConfig))

  /** Creates an intermediate Upload object that you can add Parts to. Currently, an Upload can accept at most 8 GB in total and expires
    * after an hour after you create it.
    *
    * Once you complete the Upload, we will create a File object that contains all the parts you uploaded. This File is usable in the rest
    * of our platform as a regular File object.
    *
    * For certain purposes, the correct mime_type must be specified. Please refer to documentation for the supported MIME types for your use
    * case:
    *
    * null.
    *
    * For guidance on the proper filename extensions for each purpose, please follow the documentation on creating a File.
    *
    * [[https://platform.openai.com/docs/api-reference/uploads/create]]
    *
    * @param uploadRequestBody
    *   Request body that will be used to create an upload.
    *
    * @return
    *   The Upload object with status pending.
    */
  def createUpload(uploadRequestBody: UploadRequestBody): OpenAiResponse[UploadResponse] =
    send(openAI.createUpload(uploadRequestBody))

  /** Adds a Part to an Upload object. A Part represents a chunk of bytes from the file you are trying to upload.
    *
    * Each Part can be at most 64 MB, and you can add Parts until you hit the Upload maximum of 8 GB.
    *
    * It is possible to add multiple Parts in parallel. You can decide the intended order of the Parts when you complete the Upload.
    *
    * [[https://platform.openai.com/docs/api-reference/uploads/add-part]]
    *
    * @param uploadId
    *   The ID of the Upload.
    * @param data
    *   The chunk of bytes for this Part.
    *
    * @return
    *   The upload Part object.
    */
  def addUploadPart(uploadId: String, data: File): OpenAiResponse[UploadPartResponse] =
    send(openAI.addUploadPart(uploadId, data))

  /** Completes the Upload.
    *
    * Within the returned Upload object, there is a nested File object that is ready to use in the rest of the platform.
    *
    * You can specify the order of the Parts by passing in an ordered list of the Part IDs.
    *
    * The number of bytes uploaded upon completion must match the number of bytes initially specified when creating the Upload object. No
    * Parts may be added after an Upload is completed.
    *
    * [[https://platform.openai.com/docs/api-reference/uploads/complete]]
    *
    * @param uploadId
    *   The ID of the Upload.
    * @param requestBody
    *   Request body that will be used to complete an upload.
    *
    * @return
    *   The Upload object with status completed with an additional file property containing the created usable File object.
    */
  def completeUpload(uploadId: String, requestBody: CompleteUploadRequestBody): OpenAiResponse[UploadResponse] =
    send(openAI.completeUpload(uploadId, requestBody))

  /** Cancels the Upload. No Parts may be added after an Upload is cancelled.
    *
    * [[https://platform.openai.com/docs/api-reference/uploads/cancel]]
    *
    * @param uploadId
    *   The ID of the Upload.
    *
    * @return
    *   The Upload object with status cancelled.
    */
  def cancelUpload(uploadId: String): OpenAiResponse[UploadResponse] =
    send(openAI.cancelUpload(uploadId))

  /** Creates a fine-tuning job which begins the process of creating a new model from a given dataset.
    *
    * Response includes details of the enqueued job including job status and the name of the fine-tuned models once complete.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tuning/create]]
    *
    * @param fineTuningRequestBody
    *   Request body that will be used to create a fine-tuning job.
    */
  def createFineTuningJob(fineTuningRequestBody: FineTuningJobRequestBody): OpenAiResponse[FineTuningJobResponse] =
    send(openAI.createFineTuningJob(fineTuningRequestBody))

  /** List your organization's fine-tuning jobs
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tuning/list]]
    */
  def listFineTuningJobs(
      queryParameters: finetuning.QueryParameters = finetuning.QueryParameters.empty
  ): OpenAiResponse[ListFineTuningJobResponse] =
    send(openAI.listFineTuningJobs(queryParameters))

  /** Get status updates for a fine-tuning job.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tuning/list-events]]
    *
    * @param fineTuningJobId
    *   The ID of the fine-tuning job to get checkpoints for.
    */
  def listFineTuningJobEvents(
      fineTuningJobId: String,
      queryParameters: finetuning.QueryParameters = finetuning.QueryParameters.empty
  ): OpenAiResponse[ListFineTuningJobEventResponse] =
    send(openAI.listFineTuningJobEvents(fineTuningJobId, queryParameters))

  /** List checkpoints for a fine-tuning job.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tuning/list-checkpoints]]
    *
    * @param fineTuningJobId
    *   The ID of the fine-tuning job to get checkpoints for.
    */
  def listFineTuningJobCheckpoints(
      fineTuningJobId: String,
      queryParameters: finetuning.QueryParameters = finetuning.QueryParameters.empty
  ): OpenAiResponse[ListFineTuningJobCheckpointResponse] =
    send(openAI.listFineTuningJobCheckpoints(fineTuningJobId, queryParameters))

  /** Get info about a fine-tuning job.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tuning/retrieve]]
    *
    * @param fineTuningJobId
    *   The ID of the fine-tuning job.
    */
  def retrieveFineTuningJob(fineTuningJobId: String): OpenAiResponse[FineTuningJobResponse] =
    send(openAI.retrieveFineTuningJob(fineTuningJobId))

  /** Immediately cancel a fine-tune job.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tuning/cancel]]
    *
    * @param fineTuningJobId
    *   The ID of the fine-tuning job to cancel.
    */
  def cancelFineTuningJob(fineTuningJobId: String): OpenAiResponse[FineTuningJobResponse] =
    send(openAI.cancelFineTuningJob(fineTuningJobId))

  /** Gets info about the fine-tune job.
    *
    * [[https://platform.openai.com/docs/api-reference/embeddings/create]]
    *
    * @param embeddingsBody
    *   Embeddings request body.
    */
  def createEmbeddings(embeddingsBody: EmbeddingsBody): OpenAiResponse[EmbeddingResponse] =
    send(openAI.createEmbeddings(embeddingsBody))

  /** Create a thread.
    *
    * [[https://platform.openai.com/docs/api-reference/threads/createThread]]
    *
    * @param createThreadBody
    *   Create completion request body.
    */
  def createThread(createThreadBody: CreateThreadBody): OpenAiResponse[ThreadData] =
    send(openAI.createThread(createThreadBody))

  /** Retrieves a thread.
    *
    * [[https://platform.openai.com/docs/api-reference/threads/getThread]]
    *
    * @param threadId
    *   The ID of the thread to retrieve.
    */
  def retrieveThread(threadId: String): OpenAiResponse[ThreadData] =
    send(openAI.retrieveThread(threadId))

  /** Modifies a thread.
    *
    * [[https://platform.openai.com/docs/api-reference/threads/modifyThread]]
    *
    * @param threadId
    *   The ID of the thread to modify. Only the metadata can be modified.
    */
  def modifyThread(threadId: String, metadata: Map[String, String]): OpenAiResponse[ThreadData] =
    send(openAI.modifyThread(threadId, metadata))

  /** Delete a thread.
    *
    * [[https://platform.openai.com/docs/api-reference/threads/deleteThread]]
    *
    * @param threadId
    *   The ID of the thread to delete.
    */
  def deleteThread(threadId: String): OpenAiResponse[DeleteThreadResponse] =
    send(openAI.deleteThread(threadId))

  /** Create a message.
    *
    * [[https://platform.openai.com/docs/api-reference/messages/createMessage]]
    *
    * @param threadId
    *   The ID of the thread to create a message for.
    */
  def createThreadMessage(threadId: String, message: CreateMessage): OpenAiResponse[MessageData] =
    send(openAI.createThreadMessage(threadId, message))

  /** Returns a list of messages for a given thread.
    *
    * [[https://platform.openai.com/docs/api-reference/messages/listMessages]]
    *
    * @param threadId
    *   The ID of the thread the messages belong to.
    */
  def listThreadMessages(
      threadId: String,
      queryParameters: QueryParameters = QueryParameters.empty
  ): OpenAiResponse[ListMessagesResponse] =
    send(openAI.listThreadMessages(threadId, queryParameters))

  /** Retrieve a message.
    *
    * [[https://platform.openai.com/docs/api-reference/messages/getMessage]]
    *
    * @param threadId
    *   The ID of the thread to which this message belongs.
    *
    * @param messageId
    *   The ID of the message to retrieve.
    */
  def retrieveThreadMessage(threadId: String, messageId: String): OpenAiResponse[MessageData] =
    send(openAI.retrieveThreadMessage(threadId, messageId))

  /** Modifies a message.
    *
    * [[https://platform.openai.com/docs/api-reference/messages/modifyMessage]]
    *
    * @param threadId
    *   The ID of the thread to which this message belongs.
    *
    * @param messageId
    *   The ID of the message to modify.
    */
  def modifyMessage(threadId: String, messageId: String, metadata: Map[String, String]): OpenAiResponse[MessageData] =
    send(openAI.modifyMessage(threadId, messageId, metadata))

  /** Deletes a message.
    *
    * [[https://platform.openai.com/docs/api-reference/messages/deleteMessage]]
    *
    * @param threadId
    *   The ID of the thread to which this message belongs.
    *
    * @param messageId
    *   The ID of the message to delete.
    *
    * @return
    *   Deletion status
    */
  def deleteMessage(threadId: String, messageId: String): OpenAiResponse[DeleteMessageResponse] =
    send(openAI.deleteMessage(threadId, messageId))

  /** Create an assistant with a model and instructions.
    *
    * [[https://platform.openai.com/docs/api-reference/assistants/createAssistant]]
    *
    * @param createAssistantBody
    *   Create completion request body.
    */
  def createAssistant(createAssistantBody: CreateAssistantBody): OpenAiResponse[AssistantData] =
    send(openAI.createAssistant(createAssistantBody))

  /** Returns a list of assistants.
    *
    * [[https://platform.openai.com/docs/api-reference/assistants/listAssistants]]
    */
  def listAssistants(queryParameters: QueryParameters = QueryParameters.empty): OpenAiResponse[ListAssistantsResponse] =
    send(openAI.listAssistants(queryParameters))

  /** Retrieves an assistant.
    *
    * [[https://platform.openai.com/docs/api-reference/assistants/getAssistant]]
    *
    * @param assistantId
    *   The ID of the assistant to retrieve.
    */
  def retrieveAssistant(assistantId: String): OpenAiResponse[AssistantData] =
    send(openAI.retrieveAssistant(assistantId))

  /** Modifies an assistant.
    *
    * [[https://platform.openai.com/docs/api-reference/assistants/modifyAssistant]]
    *
    * @param assistantId
    *   The ID of the assistant to modify.
    *
    * @param modifyAssistantBody
    *   Modify assistant request body.
    */
  def modifyAssistant(assistantId: String, modifyAssistantBody: ModifyAssistantBody): OpenAiResponse[AssistantData] =
    send(openAI.modifyAssistant(assistantId, modifyAssistantBody))

  /** Delete an assistant.
    *
    * [[https://platform.openai.com/docs/api-reference/assistants/deleteAssistant]]
    *
    * @param assistantId
    *   The ID of the assistant to delete.
    */
  def deleteAssistant(assistantId: String): OpenAiResponse[DeleteAssistantResponse] =
    send(openAI.deleteAssistant(assistantId))

  /** Create a run.
    *
    * [[https://platform.openai.com/docs/api-reference/runs/createRun]]
    *
    * @param threadId
    *   The ID of the thread to run.
    * @param createRun
    *   Create run request body.
    */
  def createRun(threadId: String, createRun: CreateRun): OpenAiResponse[RunData] =
    send(openAI.createRun(threadId, createRun))

  /** Create a thread and run it in one request.
    *
    * [[https://platform.openai.com/docs/api-reference/runs/createThreadAndRun]]
    *
    * @param createThreadAndRun
    *   Create thread and run request body.
    */
  def createThreadAndRun(createThreadAndRun: CreateThreadAndRun): OpenAiResponse[RunData] =
    send(openAI.createThreadAndRun(createThreadAndRun))

  /** Returns a list of runs belonging to a thread.
    *
    * [[https://platform.openai.com/docs/api-reference/runs/listRuns]]
    *
    * @param threadId
    *   The ID of the thread the run belongs to.
    */
  def listRuns(threadId: String): OpenAiResponse[ListRunsResponse] =
    send(openAI.listRuns(threadId))

  /** Returns a list of run steps belonging to a run.
    *
    * [[https://platform.openai.com/docs/api-reference/runs/listRunSteps]]
    *
    * @param threadId
    *   The ID of the thread the run and run steps belong to.
    *
    * @param runId
    *   The ID of the run the run steps belong to.
    */
  def listRunSteps(
      threadId: String,
      runId: String,
      queryParameters: QueryParameters = QueryParameters.empty
  ): OpenAiResponse[ListRunStepsResponse] =
    send(openAI.listRunSteps(threadId, runId, queryParameters))

  /** Retrieves a run.
    *
    * [[https://platform.openai.com/docs/api-reference/runs/getRun]]
    *
    * @param threadId
    *   The ID of the thread that was run.
    *
    * @param runId
    *   The ID of the run to retrieve.
    */
  def retrieveRun(threadId: String, runId: String): OpenAiResponse[RunData] =
    send(openAI.retrieveRun(threadId, runId))

  /** Retrieves a run step.
    *
    * [[https://platform.openai.com/docs/api-reference/runs/getRunStep]]
    *
    * @param threadId
    *   The ID of the thread to which the run and run step belongs.
    *
    * @param runId
    *   The ID of the run to which the run step belongs.
    *
    * @param stepId
    *   The ID of the run step to retrieve.
    */
  def retrieveRunStep(threadId: String, runId: String, stepId: String): OpenAiResponse[RunStepData] =
    send(openAI.retrieveRunStep(threadId, runId, stepId))

  /** Modifies a run.
    *
    * [[https://platform.openai.com/docs/api-reference/runs/modifyRun]]
    *
    * @param threadId
    *   The ID of the thread that was run.
    *
    * @param runId
    *   The ID of the run to modify.
    */
  def modifyRun(threadId: String, runId: String, metadata: Map[String, String]): OpenAiResponse[RunData] =
    send(openAI.modifyRun(threadId, runId, metadata))

  /** When a run has the status: "requires_action" and required_action.type is submit_tool_outputs, this endpoint can be used to submit the
    * outputs from the tool calls once they're all completed. All outputs must be submitted in a single request.
    *
    * [[https://platform.openai.com/docs/api-reference/runs/submitToolOutputs]]
    *
    * @param threadId
    *   The ID of the thread to which this run belongs.
    * @param runId
    *   The ID of the run that requires the tool output submission.
    * @param toolOutputs
    *   A list of tools for which the outputs are being submitted.
    */
  def submitToolOutputs(threadId: String, runId: String, toolOutputs: Seq[ToolOutput]): OpenAiResponse[RunData] =
    send(openAI.submitToolOutputs(threadId, runId, toolOutputs))

  /** Cancels a run that is in_progress.
    *
    * [[https://platform.openai.com/docs/api-reference/runs/cancelRun]]
    *
    * @param threadId
    *   The ID of the thread to which this run belongs.
    *
    * @param runId
    *   The ID of the run to cancel.
    */
  def cancelRun(threadId: String, runId: String): OpenAiResponse[RunData] =
    send(openAI.cancelRun(threadId, runId))

  /** Creates vector store
    *
    * @param createVectorStoreBody
    *   Options for new vector store
    * @return
    *   Newly created vector store or exception
    */
  def createVectorStore(createVectorStoreBody: CreateVectorStoreBody): OpenAiResponse[VectorStore] =
    send(openAI.createVectorStore(createVectorStoreBody))

  /** Lists vector store
    *
    * @param queryParameters
    *   Search params
    * @return
    *   List of vector stores matching criteria or exception
    */
  def listVectorStores(queryParameters: QueryParameters = QueryParameters.empty): OpenAiResponse[ListVectorStoresResponse] =
    send(openAI.listVectorStores(queryParameters))

  /** Retrieves vector store by id
    *
    * @param vectorStoreId
    *   Id of vector store
    * @return
    *   Vector store object or exception
    */
  def retrieveVectorStore(vectorStoreId: String): OpenAiResponse[VectorStore] =
    send(openAI.retrieveVectorStore(vectorStoreId))

  /** Modifies vector store
    *
    * @param vectorStoreId
    *   Id of vector store to modify
    * @param modifyVectorStoreBody
    *   New values for store properties
    * @return
    *   Modified vector store object
    */
  def modifyVectorStore(vectorStoreId: String, modifyVectorStoreBody: ModifyVectorStoreBody): OpenAiResponse[VectorStore] =
    send(openAI.modifyVectorStore(vectorStoreId, modifyVectorStoreBody))

  /** Deletes vector store
    *
    * @param vectorStoreId
    *   Id of vector store to be deleted
    * @return
    *   Result of deleted operation
    */
  def deleteVectorStore(vectorStoreId: String): OpenAiResponse[DeleteVectorStoreResponse] =
    send(openAI.deleteVectorStore(vectorStoreId))

  /** Creates vector store file
    *
    * @param vectorStoreId
    *   Id of vector store for file
    * @param createVectorStoreFileBody
    *   Properties of file
    * @return
    *   Newly created vector store file
    */
  def createVectorStoreFile(
      vectorStoreId: String,
      createVectorStoreFileBody: CreateVectorStoreFileBody
  ): OpenAiResponse[VectorStoreFile] =
    send(openAI.createVectorStoreFile(vectorStoreId, createVectorStoreFileBody))

  /** List files belonging to particular datastore
    *
    * @param vectorStoreId
    *   Id of vector store
    * @param queryParameters
    *   Search params
    * @return
    *   List of vector store files
    */
  def listVectorStoreFiles(
      vectorStoreId: String,
      queryParameters: ListVectorStoreFilesBody = ListVectorStoreFilesBody()
  ): OpenAiResponse[ListVectorStoreFilesResponse] =
    send(openAI.listVectorStoreFiles(vectorStoreId, queryParameters))

  /** Retrieves vector store file by id
    *
    * @param vectorStoreId
    *   Id of vector store
    * @param fileId
    *   Id of vector store file
    * @return
    *   Vector store file
    */
  def retrieveVectorStoreFile(vectorStoreId: String, fileId: String): OpenAiResponse[VectorStoreFile] =
    send(openAI.retrieveVectorStoreFile(vectorStoreId, fileId))

  /** Deletes vector store file by id
    *
    * @param vectorStoreId
    *   Id of vector store
    * @param fileId
    *   Id of vector store file
    * @return
    *   Result of delete operation
    */
  def deleteVectorStoreFile(vectorStoreId: String, fileId: String): OpenAiResponse[DeleteVectorStoreFileResponse] =
    send(openAI.deleteVectorStoreFile(vectorStoreId, fileId))

  /** Creates and executes a batch from an uploaded file of requests
    *
    * [[https://platform.openai.com/docs/api-reference/batch/create]]
    *
    * @param createBatchRequest
    *   Request body that will be used to create a batch.
    * @return
    *   The created Batch object.
    */
  def createBatch(createBatchRequest: BatchRequestBody): OpenAiResponse[BatchResponse] =
    send(openAI.createBatch(createBatchRequest))

  /** Retrieves a batch.
    *
    * [[https://platform.openai.com/docs/api-reference/batch/retreive]]
    *
    * @param batchId
    *   The ID of the batch to retrieve.
    * @return
    *   The Batch object matching the specified ID.
    */
  def retrieveBatch(batchId: String): OpenAiResponse[BatchResponse] =
    send(openAI.retrieveBatch(batchId))

  /** Cancels an in-progress batch. The batch will be in status cancelling for up to 10 minutes, before changing to cancelled, where it will
    * have partial results (if any) available in the output file.
    *
    * [[https://platform.openai.com/docs/api-reference/batch/cancel]]
    *
    * @param batchId
    *   The ID of the batch to cancel.
    * @return
    *   The Batch object matching the specified ID.
    */
  def cancelBatch(batchId: String): OpenAiResponse[BatchResponse] =
    send(openAI.cancelBatch(batchId))

  /** List your organization's batches.
    *
    * [[https://platform.openai.com/docs/api-reference/batch/list]]
    *
    * @return
    *   A list of paginated Batch objects.
    */
  def listBatches(queryParameters: batch.QueryParameters = batch.QueryParameters.empty): OpenAiResponse[ListBatchResponse] =
    send(openAI.listBatches(queryParameters))

  /** Create an organization admin API key
    *
    * [[https://platform.openai.com/docs/api-reference/admin-api-keys/create]]
    *
    * @param createAdminApiKeyRequest
    *   Request body that will be used to create an admin API key.
    * @return
    *   The created admin API key object.
    */
  def createAdminApiKey(createAdminApiKeyRequest: AdminApiKeyRequestBody): OpenAiResponse[AdminApiKeyResponse] =
    send(openAI.createAdminApiKey(createAdminApiKeyRequest))

  /** Retrieve a single organization API key
    *
    * [[https://platform.openai.com/docs/api-reference/admin-api-keys/listget]]
    *
    * @param keyId
    *   Key id used to retrieve an admin API key.
    * @return
    *   The requested admin API key object.
    */
  def retrieveAdminApiKey(keyId: String): OpenAiResponse[AdminApiKeyResponse] =
    send(openAI.retrieveAdminApiKey(keyId))

  /** List organization API keys
    *
    * [[https://platform.openai.com/docs/api-reference/admin-api-keys/list]]
    *
    * @return
    *   A list of admin API key objects.
    */
  def listAdminApiKeys(queryParameters: admin.QueryParameters = admin.QueryParameters.empty): OpenAiResponse[ListAdminApiKeyResponse] =
    send(openAI.listAdminApiKeys(queryParameters))

  /** Delete an organization admin API key
    *
    * [[https://platform.openai.com/docs/api-reference/admin-api-keys/delete]]
    *
    * @param keyId
    *   Key id used to delete an admin API key.
    * @return
    *   A confirmation object indicating the key was deleted.
    */
  def deleteAdminApiKey(keyId: String): OpenAiResponse[DeleteAdminApiKeyResponse] =
    send(openAI.deleteAdminApiKey(keyId))

  /** Closes and releases resources of http client if was not provided explicitly, otherwise works no-op. */
  // def close(): IO[Nothing, Unit] = if (closeClient) backend.close().orDie else ZIO.unit

  private def send[A](request: Request[Either[OpenAIException, A]]): OpenAiResponse[A] =
    request.send(backend).orDie.flatMap(response => ZIO.fromEither(response.body))
}

object OpenAIZioClient {
  type OpenAiResponse[A] = IO[OpenAIException, A]

  val layer = ZLayer {
    for {
      openAI <- ZIO.service[OpenAI]
      client <- ZIO.service[WebSocketStreamBackend[Task, ZioStreams]]
    } yield new OpenAIZioClient(openAI, client)
  }
}
