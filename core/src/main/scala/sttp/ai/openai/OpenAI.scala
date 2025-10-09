package sttp.openai

import sttp.capabilities.Streams
import sttp.client4._
import sttp.model.{Header, Uri}
import sttp.openai.OpenAIExceptions.OpenAIException
import sttp.openai.json.SttpUpickleApiExtension._
import sttp.openai.requests.admin.{QueryParameters => _, _}
import sttp.openai.requests.assistants.AssistantsRequestBody.{CreateAssistantBody, ModifyAssistantBody}
import sttp.openai.requests.assistants.AssistantsResponseData.{AssistantData, DeleteAssistantResponse, ListAssistantsResponse}
import sttp.openai.requests.audio.AudioResponseData.AudioResponse
import sttp.openai.requests.audio.speech.SpeechRequestBody
import sttp.openai.requests.audio.transcriptions.{TranscriptionConfig, TranscriptionModel}
import sttp.openai.requests.audio.translations.{TranslationConfig, TranslationModel}
import sttp.openai.requests.batch.{QueryParameters => _, _}
import sttp.openai.requests.completions.CompletionsRequestBody.CompletionsBody
import sttp.openai.requests.completions.CompletionsResponseData.CompletionsResponse
import sttp.openai.requests.completions.chat
import sttp.openai.requests.completions.chat.ChatRequestBody.{ChatBody, UpdateChatCompletionRequestBody}
import sttp.openai.requests.completions.chat.ChatRequestResponseData.{
  ChatResponse,
  DeleteChatCompletionResponse,
  ListChatResponse,
  ListMessageResponse
}
import sttp.openai.requests.completions.chat.{ListMessagesQueryParameters => _}
import sttp.openai.requests.embeddings.EmbeddingsRequestBody.EmbeddingsBody
import sttp.openai.requests.embeddings.EmbeddingsResponseBody.EmbeddingResponse
import sttp.openai.requests.files.FilesResponseData._
import sttp.openai.requests.finetuning._
import sttp.openai.requests.images.ImageResponseData.ImageResponse
import sttp.openai.requests.images.creation.ImageCreationRequestBody.ImageCreationBody
import sttp.openai.requests.images.edit.ImageEditsConfig
import sttp.openai.requests.images.variations.ImageVariationsConfig
import sttp.openai.requests.models.ModelsResponseData.{DeletedModelData, ModelData, ModelsResponse}
import sttp.openai.requests.moderations.ModerationsRequestBody.ModerationsBody
import sttp.openai.requests.moderations.ModerationsResponseData.ModerationData
import sttp.openai.requests.responses._
import sttp.openai.requests.threads.QueryParameters
import sttp.openai.requests.threads.ThreadsRequestBody.CreateThreadBody
import sttp.openai.requests.threads.ThreadsResponseData.{DeleteThreadResponse, ThreadData}
import sttp.openai.requests.threads.messages.ThreadMessagesRequestBody.CreateMessage
import sttp.openai.requests.threads.messages.ThreadMessagesResponseData.{DeleteMessageResponse, ListMessagesResponse, MessageData}
import sttp.openai.requests.threads.runs.ThreadRunsRequestBody._
import sttp.openai.requests.threads.runs.ThreadRunsResponseData.{ListRunStepsResponse, ListRunsResponse, RunData, RunStepData}
import sttp.openai.requests.upload.{CompleteUploadRequestBody, UploadPartResponse, UploadRequestBody, UploadResponse}
import sttp.openai.requests.vectorstore.VectorStoreRequestBody.{CreateVectorStoreBody, ModifyVectorStoreBody}
import sttp.openai.requests.vectorstore.VectorStoreResponseData.{DeleteVectorStoreResponse, ListVectorStoresResponse, VectorStore}
import sttp.openai.requests.vectorstore.file.VectorStoreFileRequestBody.{CreateVectorStoreFileBody, ListVectorStoreFilesBody}
import sttp.openai.requests.vectorstore.file.VectorStoreFileResponseData.{
  DeleteVectorStoreFileResponse,
  ListVectorStoreFilesResponse,
  VectorStoreFile
}
import sttp.openai.requests.{admin, batch, finetuning}

import java.io.{File, InputStream}
import java.nio.file.Paths

class OpenAI(authToken: String, baseUri: Uri = OpenAIUris.OpenAIBaseUri) {

  private val openAIUris = new OpenAIUris(baseUri)

  /** Lists the currently available models, and provides basic information about each one such as the owner and availability.
    *
    * [[https://platform.openai.com/docs/api-reference/models]]
    */
  def getModels: Request[Either[OpenAIException, ModelsResponse]] =
    openAIAuthRequest
      .get(openAIUris.Models)
      .response(asJson_parseErrors[ModelsResponse])

  /** Retrieves a model instance, providing basic information about the model such as the owner and permissions.
    *
    * [[https://platform.openai.com/docs/api-reference/models/retrieve]]
    *
    * @param modelId
    *   The ID of the model to use for this request.
    */
  def retrieveModel(modelId: String): Request[Either[OpenAIException, ModelData]] =
    openAIAuthRequest
      .get(openAIUris.model(modelId))
      .response(asJson_parseErrors[ModelData])

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
  def deleteModel(modelId: String): Request[Either[OpenAIException, DeletedModelData]] =
    openAIAuthRequest
      .delete(openAIUris.model(modelId))
      .response(asJson_parseErrors[DeletedModelData])

  /** Creates a completion for the provided prompt and parameters given in request body.
    *
    * [[https://platform.openai.com/docs/api-reference/completions/create]]
    *
    * @param completionBody
    *   Create completion request body.
    * @deprecated
    *   This is marked as Legacy in OpenAI API and might be removed in the future. Please use [[createChatCompletion]] instead.
    */
  def createCompletion(completionBody: CompletionsBody): Request[Either[OpenAIException, CompletionsResponse]] =
    openAIAuthRequest
      .post(openAIUris.Completions)
      .body(asJson(completionBody))
      .response(asJson_parseErrors[CompletionsResponse])

  /** Creates an image given a prompt in request body.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create]]
    *
    * @param imageCreationBody
    *   Create image request body.
    */
  def createImage(imageCreationBody: ImageCreationBody): Request[Either[OpenAIException, ImageResponse]] =
    openAIAuthRequest
      .post(openAIUris.CreateImage)
      .body(asJson(imageCreationBody))
      .response(asJson_parseErrors[ImageResponse])

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
  def imageEdits(image: File, prompt: String): Request[Either[OpenAIException, ImageResponse]] =
    openAIAuthRequest
      .post(openAIUris.EditImage)
      .multipartBody(
        multipart("prompt", prompt),
        multipartFile("image", image)
      )
      .response(asJson_parseErrors[ImageResponse])

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
  def imageEdits(systemPath: String, prompt: String): Request[Either[OpenAIException, ImageResponse]] =
    openAIAuthRequest
      .post(openAIUris.EditImage)
      .multipartBody(
        multipart("prompt", prompt),
        multipartFile("image", Paths.get(systemPath).toFile)
      )
      .response(asJson_parseErrors[ImageResponse])

  /** Creates edited or extended images given an original image and a prompt.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-edit]]
    *
    * @param imageEditsConfig
    *   An instance of the case class ImageEditConfig containing the necessary parameters for editing the image.
    */
  def imageEdits(
      imageEditsConfig: ImageEditsConfig
  ): Request[Either[OpenAIException, ImageResponse]] =
    openAIAuthRequest
      .post(openAIUris.EditImage)
      .multipartBody {
        import imageEditsConfig._
        val imageParts = image match {
          case singleImage :: Nil => Seq(multipartFile("image", singleImage))
          case _                  => image.map(img => multipartFile("image[]", img))
        }
        imageParts ++ Seq(
          Some(multipart("prompt", prompt)),
          background.map(bg => multipart("background", bg)),
          inputFidelity.map(fid => multipart("input_fidelity", fid)),
          mask.map(multipartFile("mask", _)),
          model.map(m => multipart("model", m.value)),
          n.map(i => multipart("n", i.toString)),
          outputCompression.map(c => multipart("output_compression", c.toString)),
          outputFormat.map(f => multipart("output_format", f)),
          partialImages.map(p => multipart("partial_images", p.toString)),
          quality.map(q => multipart("quality", q)),
          size.map(s => multipart("size", s.value)),
          responseFormat.map(format => multipart("response_format", format.value)),
          stream.map(s => multipart("stream", s.toString)),
          user.map(u => multipart("user", u))
        ).flatten
      }
      .response(asJson_parseErrors[ImageResponse])

  /** Creates a variation of a given image.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-variation]]
    *
    * @param image
    *   The image to use as the basis for the variation.
    *
    * Must be a valid PNG file, less than 4MB, and square.
    */
  def imageVariations(
      image: File
  ): Request[Either[OpenAIException, ImageResponse]] =
    openAIAuthRequest
      .post(openAIUris.VariationsImage)
      .multipartBody(
        multipartFile("image", image)
      )
      .response(asJson_parseErrors[ImageResponse])

  /** Creates a variation of a given image.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-variation]]
    *
    * @param systemPath
    *   Path to the image to use as the basis for the variation.
    *
    * Must be a valid PNG file, less than 4MB, and square.
    */
  def imageVariations(
      systemPath: String
  ): Request[Either[OpenAIException, ImageResponse]] =
    openAIAuthRequest
      .post(openAIUris.VariationsImage)
      .multipartBody(
        multipartFile("image", Paths.get(systemPath).toFile)
      )
      .response(asJson_parseErrors[ImageResponse])

  /** Creates a variation of a given image.
    *
    * [[https://platform.openai.com/docs/api-reference/images/create-variation]]
    *
    * @param imageVariationsConfig
    *   An instance of the case class ImageVariationsConfig containing the necessary parameters for the image variation.
    */
  def imageVariations(
      imageVariationsConfig: ImageVariationsConfig
  ): Request[Either[OpenAIException, ImageResponse]] =
    openAIAuthRequest
      .post(openAIUris.VariationsImage)
      .multipartBody {
        import imageVariationsConfig._
        Seq(
          Some(multipartFile("image", image)),
          n.map(i => multipart("n", i.toString)),
          size.map(s => multipart("size", s.value)),
          responseFormat.map(format => multipart("response_format", format.value)),
          user.map(multipart("user", _))
        ).flatten
      }
      .response(asJson_parseErrors[ImageResponse])

  /** Creates a model response for the given chat conversation defined in chatBody.
    *
    * [[https://platform.openai.com/docs/api-reference/chat/create]]
    *
    * @param chatBody
    *   Chat request body.
    */
  def createChatCompletion(chatBody: ChatBody): Request[Either[OpenAIException, ChatResponse]] =
    openAIAuthRequest
      .post(openAIUris.ChatCompletions)
      .body(asJson(chatBody))
      .response(asJson_parseErrors[ChatResponse])

  /** Creates a model response for the given chat conversation defined in chatBody.
    *
    * The response is streamed in chunks as server-sent events, which are returned unparsed as a binary stream, using the given streams
    * implementation.
    *
    * [[https://platform.openai.com/docs/api-reference/chat/create]]
    *
    * @param s
    *   The streams implementation to use.
    * @param chatBody
    *   Chat request body.
    */
  def createChatCompletionAsBinaryStream[S](s: Streams[S], chatBody: ChatBody): StreamRequest[Either[OpenAIException, s.BinaryStream], S] =
    openAIAuthRequest
      .post(openAIUris.ChatCompletions)
      .body(asJson(ChatBody.withStreaming(chatBody)))
      .response(asStreamUnsafe_parseErrors(s))

  /** Creates a model response for the given chat conversation defined in chatBody.
    *
    * The response is streamed in chunks as server-sent events, which are returned unparsed as a [[InputStream]].
    *
    * [[https://platform.openai.com/docs/api-reference/chat/create]]
    *
    * @param chatBody
    *   Chat request body.
    */
  def createChatCompletionAsInputStream(chatBody: ChatBody): Request[Either[OpenAIException, InputStream]] =
    openAIAuthRequest
      .post(openAIUris.ChatCompletions)
      .body(asJson(ChatBody.withStreaming(chatBody)))
      .response(asInputStreamUnsafe_parseErrors)

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
  def getChatCompletion(completionId: String): Request[Either[OpenAIException, ChatResponse]] =
    openAIAuthRequest
      .get(openAIUris.chatCompletion(completionId))
      .response(asJson_parseErrors[ChatResponse])

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
  ): Request[Either[OpenAIException, ListMessageResponse]] = {
    val uri = openAIUris
      .chatMessages(completionId)
      .withParams(queryParameters.toMap)

    openAIAuthRequest
      .get(uri)
      .response(asJson_parseErrors[ListMessageResponse])
  }

  /** List stored chat completions. Only chat completions that have been stored with the store parameter set to true will be returned.
    *
    * [[https://platform.openai.com/docs/api-reference/chat/list]]
    *
    * @return
    *   A list of chat completions matching the specified filters.
    */
  def listChatCompletions(
      queryParameters: chat.ListChatCompletionsQueryParameters = chat.ListChatCompletionsQueryParameters.empty
  ): Request[Either[OpenAIException, ListChatResponse]] = {
    val uri = openAIUris.ChatCompletions
      .withParams(queryParameters.toMap)

    openAIAuthRequest
      .get(uri)
      .response(asJson_parseErrors[ListChatResponse])
  }

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
  ): Request[Either[OpenAIException, ChatResponse]] =
    openAIAuthRequest
      .post(openAIUris.chatCompletion(completionId))
      .body(asJson(requestBody))
      .response(asJson_parseErrors[ChatResponse])

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
  def deleteChatCompletion(completionId: String): Request[Either[OpenAIException, DeleteChatCompletionResponse]] =
    openAIAuthRequest
      .delete(openAIUris.chatCompletion(completionId))
      .response(asJson_parseErrors[DeleteChatCompletionResponse])

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
  def createModelResponse(requestBody: ResponsesRequestBody): Request[Either[OpenAIException, ResponsesResponseBody]] =
    openAIAuthRequest
      .post(openAIUris.Responses)
      .body(asJson(requestBody))
      .response(asJson_parseErrors[ResponsesResponseBody])

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
  ): Request[Either[OpenAIException, ResponsesResponseBody]] = {
    val uri = openAIUris
      .response(responseId)
      .withParams(queryParameters.toMap)

    openAIAuthRequest
      .get(uri)
      .response(asJson_parseErrors[ResponsesResponseBody])
  }

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
  def deleteModelResponse(responseId: String): Request[Either[OpenAIException, DeleteModelResponseResponse]] =
    openAIAuthRequest
      .delete(openAIUris.response(responseId))
      .response(asJson_parseErrors[DeleteModelResponseResponse])

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
  def cancelResponse(responseId: String): Request[Either[OpenAIException, ResponsesResponseBody]] =
    openAIAuthRequest
      .post(openAIUris.cancelResponse(responseId))
      .response(asJson_parseErrors[ResponsesResponseBody])

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
  ): Request[Either[OpenAIException, InputItemsListResponseBody]] = {
    val uri = openAIUris
      .responseInputItems(responseId)
      .withParams(queryParameters.toMap)

    openAIAuthRequest
      .get(uri)
      .response(asJson_parseErrors[InputItemsListResponseBody])
  }

  /** Returns a list of files that belong to the user's organization.
    *
    * [[https://platform.openai.com/docs/api-reference/files]]
    */
  def getFiles: Request[Either[OpenAIException, FilesResponse]] =
    openAIAuthRequest
      .get(openAIUris.Files)
      .response(asJson_parseErrors[FilesResponse])

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
  def uploadFile(file: File, purpose: String): Request[Either[OpenAIException, FileData]] =
    openAIAuthRequest
      .post(openAIUris.Files)
      .multipartBody(
        multipart("purpose", purpose),
        multipartFile("file", file)
      )
      .response(asJson_parseErrors[FileData])

  /** Upload a file that contains document(s) to be used across various endpoints/features. Currently, the size of all the files uploaded by
    * one organization can be up to 1 GB. Please contact OpenAI if you need to increase the storage limit.
    *
    * [[https://platform.openai.com/docs/api-reference/files/upload]]
    *
    * @param file
    *   JSON Lines file to be uploaded and the purpose is set to "fine-tune", each line is a JSON record with "prompt" and "completion"
    *   fields representing your [[https://platform.openai.com/docs/guides/fine-tuning/prepare-training-data training examples]].
    */
  def uploadFile(file: File): Request[Either[OpenAIException, FileData]] =
    openAIAuthRequest
      .post(openAIUris.Files)
      .multipartBody(
        multipart("purpose", "fine-tune"),
        multipartFile("file", file)
      )
      .response(asJson_parseErrors[FileData])

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
  def uploadFile(systemPath: String, purpose: String): Request[Either[OpenAIException, FileData]] =
    openAIAuthRequest
      .post(openAIUris.Files)
      .multipartBody(
        multipart("purpose", purpose),
        multipartFile("file", Paths.get(systemPath).toFile)
      )
      .response(asJson_parseErrors[FileData])

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
  def uploadFile(systemPath: String): Request[Either[OpenAIException, FileData]] =
    openAIAuthRequest
      .post(openAIUris.Files)
      .multipartBody(
        multipart("purpose", "fine-tune"),
        multipartFile("file", Paths.get(systemPath).toFile)
      )
      .response(asJson_parseErrors[FileData])

  /** Delete a file.
    *
    * [[https://platform.openai.com/docs/api-reference/files/delete]]
    *
    * @param fileId
    *   The ID of the file to use for this request.
    */
  def deleteFile(fileId: String): Request[Either[OpenAIException, DeletedFileData]] =
    openAIAuthRequest
      .delete(openAIUris.file(fileId))
      .response(asJson_parseErrors[DeletedFileData])

  /** Returns information about a specific file.
    *
    * [[https://platform.openai.com/docs/api-reference/files/retrieve]]
    *
    * @param fileId
    *   The ID of the file to use for this request.
    */
  def retrieveFile(fileId: String): Request[Either[OpenAIException, FileData]] =
    openAIAuthRequest
      .get(openAIUris.file(fileId))
      .response(asJson_parseErrors[FileData])

  /** Returns the contents of the specified file.
    *
    * [[https://platform.openai.com/docs/api-reference/files/retrieve-content]]
    *
    * @param fileId
    *   The ID of the file.
    */
  def retrieveFileContent(fileId: String): Request[Either[OpenAIException, String]] =
    openAIAuthRequest
      .get(openAIUris.fileContent(fileId))
      .response(asStringEither)

  /** Generates audio from the input text.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/createSpeech]]
    *
    * @param s
    *   The streams implementation to use.
    * @param requestBody
    *   Request body that will be used to create a speech.
    *
    * @return
    *   The audio file content.
    */
  def createSpeechAsBinaryStream[S](
      s: Streams[S],
      requestBody: SpeechRequestBody
  ): StreamRequest[Either[OpenAIException, s.BinaryStream], S] =
    openAIAuthRequest
      .post(openAIUris.Speech)
      .body(asJson(requestBody))
      .response(asStreamUnsafe_parseErrors(s))

  /** Generates audio from the input text.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/createSpeech]]
    *
    * @param requestBody
    *   Request body that will be used to create a speech.
    *
    * @return
    *   The audio file content.
    */
  def createSpeechAsInputStream(requestBody: SpeechRequestBody): Request[Either[OpenAIException, InputStream]] =
    openAIAuthRequest
      .post(openAIUris.Speech)
      .body(asJson(requestBody))
      .response(asInputStreamUnsafe_parseErrors)

  /** Translates audio into English text.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param file
    *   The audio file to translate, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or webm.
    * @param model
    *   ID of the model to use. Only whisper-1 is currently available.
    */
  def createTranslation(file: File, model: TranslationModel): Request[Either[OpenAIException, AudioResponse]] =
    openAIAuthRequest
      .post(openAIUris.Translations)
      .multipartBody(
        multipartFile("file", file),
        multipart("model", model.value)
      )
      .response(asJson_parseErrors[AudioResponse])

  /** Translates audio into English text.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param systemPath
    *   The audio systemPath to transcribe, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or webm.
    * @param model
    *   ID of the model to use. Only whisper-1 is currently available.
    */
  def createTranslation(systemPath: String, model: TranslationModel): Request[Either[OpenAIException, AudioResponse]] =
    openAIAuthRequest
      .post(openAIUris.Translations)
      .multipartBody(
        multipartFile("file", Paths.get(systemPath).toFile),
        multipart("model", asJson(model))
      )
      .response(asJson_parseErrors[AudioResponse])

  /** Translates audio into English text.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param translationConfig
    *   An instance of the case class TranslationConfig containing the necessary parameters for the audio translation.
    */
  def createTranslation(translationConfig: TranslationConfig): Request[Either[OpenAIException, AudioResponse]] =
    openAIAuthRequest
      .post(openAIUris.Translations)
      .multipartBody {
        import translationConfig._
        Seq(
          Some(multipartFile("file", file)),
          Some(multipart("model", model.value)),
          prompt.map(multipart("prompt", _)),
          responseFormat.map(format => multipart("response_format", asJson(format))),
          temperature.map(i => multipart("temperature", i.toString)),
          language.map(multipart("language", _))
        ).flatten
      }
      .response(asJson_parseErrors[AudioResponse])

  /** Classifies if text violates OpenAI's Content Policy.
    *
    * [[https://platform.openai.com/docs/api-reference/moderations/create]]
    *
    * @param moderationsBody
    *   Moderation request body.
    */
  def createModeration(moderationsBody: ModerationsBody): Request[Either[OpenAIException, ModerationData]] =
    openAIAuthRequest
      .post(openAIUris.Moderations)
      .body(asJson(moderationsBody))
      .response(asJson_parseErrors[ModerationData])

  /** Transcribes audio into the input language.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param file
    *   The audio file to transcribe, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or webm.
    * @param model
    *   ID of the model to use. Whisper-1, gpt-4o-transcribe, gpt-4o-mini-transcribe are currently available.
    */
  def createTranscription(file: File, model: TranscriptionModel): Request[Either[OpenAIException, AudioResponse]] =
    openAIAuthRequest
      .post(openAIUris.Transcriptions)
      .multipartBody(
        multipartFile("file", file),
        multipart("model", model.value)
      )
      .response(asJson_parseErrors[AudioResponse])

  /** Transcribes audio into the input language.
    *
    * [[https://platform.openai.com/docs/api-reference/audio/create]]
    *
    * @param systemPath
    *   The audio systemPath to transcribe, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or webm.
    * @param model
    *   ID of the model to use. Whisper-1, gpt-4o-transcribe, gpt-4o-mini-transcribe are currently available.
    */
  def createTranscription(
      systemPath: String,
      model: TranscriptionModel
  ): Request[Either[OpenAIException, AudioResponse]] =
    openAIAuthRequest
      .post(openAIUris.Transcriptions)
      .multipartBody(
        multipartFile("file", Paths.get(systemPath).toFile),
        multipart("model", model.value)
      )
      .response(asJson_parseErrors[AudioResponse])

  /** Transcribes audio into the input language.
    *
    * @param transcriptionConfig
    *   An instance of the case class TranscriptionConfig containing the necessary parameters for the audio transcription
    * @return
    *   An url to edited image.
    */
  def createTranscription(transcriptionConfig: TranscriptionConfig): Request[Either[OpenAIException, AudioResponse]] =
    openAIAuthRequest
      .post(openAIUris.Transcriptions)
      .multipartBody {
        import transcriptionConfig._
        Seq(
          Some(multipartFile("file", file)),
          Some(multipart("model", model.value)),
          prompt.map(multipart("prompt", _)),
          responseFormat.map(format => multipart("response_format", format.value)),
          temperature.map(t => multipart("temperature", t.toString)),
          language.map(lang => multipart("language", lang.value))
        ).flatten
      }
      .response(asJson_parseErrors[AudioResponse])

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
  def createUpload(uploadRequestBody: UploadRequestBody): Request[Either[OpenAIException, UploadResponse]] =
    openAIAuthRequest
      .post(openAIUris.Uploads)
      .body(asJson(uploadRequestBody))
      .response(asJson_parseErrors[UploadResponse])

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
  def addUploadPart(uploadId: String, data: File): Request[Either[OpenAIException, UploadPartResponse]] =
    openAIAuthRequest
      .post(openAIUris.uploadParts(uploadId))
      .multipartBody(multipartFile("data", data))
      .response(asJson_parseErrors[UploadPartResponse])

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
  def completeUpload(uploadId: String, requestBody: CompleteUploadRequestBody): Request[Either[OpenAIException, UploadResponse]] =
    openAIAuthRequest
      .post(openAIUris.completeUpload(uploadId))
      .body(asJson(requestBody))
      .response(asJson_parseErrors[UploadResponse])

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
  def cancelUpload(uploadId: String): Request[Either[OpenAIException, UploadResponse]] =
    openAIAuthRequest
      .post(openAIUris.cancelUpload(uploadId))
      .response(asJson_parseErrors[UploadResponse])

  /** Creates a fine-tuning job which begins the process of creating a new model from a given dataset.
    *
    * Response includes details of the enqueued job including job status and the name of the fine-tuned models once complete.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tuning/create]]
    *
    * @param fineTuningRequestBody
    *   Request body that will be used to create a fine-tuning job.
    */
  def createFineTuningJob(fineTuningRequestBody: FineTuningJobRequestBody): Request[Either[OpenAIException, FineTuningJobResponse]] =
    openAIAuthRequest
      .post(openAIUris.FineTuningJobs)
      .body(asJson(fineTuningRequestBody))
      .response(asJson_parseErrors[FineTuningJobResponse])

  /** List your organization's fine-tuning jobs
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tuning/list]]
    */
  def listFineTuningJobs(
      queryParameters: finetuning.QueryParameters = finetuning.QueryParameters.empty
  ): Request[Either[OpenAIException, ListFineTuningJobResponse]] = {
    val uri = openAIUris.FineTuningJobs
      .withParams(queryParameters.toMap)

    openAIAuthRequest
      .get(uri)
      .response(asJson_parseErrors[ListFineTuningJobResponse])
  }

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
  ): Request[Either[OpenAIException, ListFineTuningJobEventResponse]] = {
    val uri = openAIUris
      .fineTuningJobEvents(fineTuningJobId)
      .withParams(queryParameters.toMap)

    openAIAuthRequest
      .get(uri)
      .response(asJson_parseErrors[ListFineTuningJobEventResponse])
  }

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
  ): Request[Either[OpenAIException, ListFineTuningJobCheckpointResponse]] = {
    val uri = openAIUris
      .fineTuningJobCheckpoints(fineTuningJobId)
      .withParams(queryParameters.toMap)

    openAIAuthRequest
      .get(uri)
      .response(asJson_parseErrors[ListFineTuningJobCheckpointResponse])
  }

  /** Get info about a fine-tuning job.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tuning/retrieve]]
    *
    * @param fineTuningJobId
    *   The ID of the fine-tuning job.
    */
  def retrieveFineTuningJob(fineTuningJobId: String): Request[Either[OpenAIException, FineTuningJobResponse]] =
    openAIAuthRequest
      .get(openAIUris.fineTuningJob(fineTuningJobId))
      .response(asJson_parseErrors[FineTuningJobResponse])

  /** Immediately cancel a fine-tune job.
    *
    * [[https://platform.openai.com/docs/api-reference/fine-tuning/cancel]]
    *
    * @param fineTuningJobId
    *   The ID of the fine-tuning job to cancel.
    */
  def cancelFineTuningJob(fineTuningJobId: String): Request[Either[OpenAIException, FineTuningJobResponse]] =
    openAIAuthRequest
      .post(openAIUris.cancelFineTuningJob(fineTuningJobId))
      .response(asJson_parseErrors[FineTuningJobResponse])

  /** Gets info about the fine-tune job.
    *
    * [[https://platform.openai.com/docs/api-reference/embeddings/create]]
    *
    * @param embeddingsBody
    *   Embeddings request body.
    */
  def createEmbeddings(embeddingsBody: EmbeddingsBody): Request[Either[OpenAIException, EmbeddingResponse]] =
    openAIAuthRequest
      .post(openAIUris.Embeddings)
      .body(asJson(embeddingsBody))
      .response(asJson_parseErrors[EmbeddingResponse])

  /** Create a thread.
    *
    * [[https://platform.openai.com/docs/api-reference/threads/createThread]]
    *
    * @param createThreadBody
    *   Create completion request body.
    */
  def createThread(createThreadBody: CreateThreadBody): Request[Either[OpenAIException, ThreadData]] =
    betaOpenAIAuthRequest
      .post(openAIUris.Threads)
      .body(asJson(createThreadBody))
      .response(asJson_parseErrors[ThreadData])

  /** Retrieves a thread.
    *
    * [[https://platform.openai.com/docs/api-reference/threads/getThread]]
    *
    * @param threadId
    *   The ID of the thread to retrieve.
    */
  def retrieveThread(threadId: String): Request[Either[OpenAIException, ThreadData]] =
    betaOpenAIAuthRequest
      .get(openAIUris.thread(threadId))
      .response(asJson_parseErrors[ThreadData])

  /** Modifies a thread.
    *
    * [[https://platform.openai.com/docs/api-reference/threads/modifyThread]]
    *
    * @param threadId
    *   The ID of the thread to modify. Only the metadata can be modified.
    */
  def modifyThread(threadId: String, metadata: Map[String, String]): Request[Either[OpenAIException, ThreadData]] =
    betaOpenAIAuthRequest
      .post(openAIUris.thread(threadId))
      .body(metadata)
      .response(asJson_parseErrors[ThreadData])

  /** Delete a thread.
    *
    * [[https://platform.openai.com/docs/api-reference/threads/deleteThread]]
    *
    * @param threadId
    *   The ID of the thread to delete.
    */
  def deleteThread(threadId: String): Request[Either[OpenAIException, DeleteThreadResponse]] =
    betaOpenAIAuthRequest
      .delete(openAIUris.thread(threadId))
      .response(asJson_parseErrors[DeleteThreadResponse])

  /** Create a message.
    *
    * [[https://platform.openai.com/docs/api-reference/messages/createMessage]]
    *
    * @param threadId
    *   The ID of the thread to create a message for.
    */
  def createThreadMessage(threadId: String, message: CreateMessage): Request[Either[OpenAIException, MessageData]] =
    betaOpenAIAuthRequest
      .post(openAIUris.threadMessages(threadId))
      .body(asJson(message))
      .response(asJson_parseErrors[MessageData])

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
  ): Request[Either[OpenAIException, ListMessagesResponse]] = {
    val uri = openAIUris
      .threadMessages(threadId)
      .withParams(queryParameters.toMap)

    betaOpenAIAuthRequest
      .get(uri)
      .response(asJson_parseErrors[ListMessagesResponse])
  }

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
  def retrieveThreadMessage(
      threadId: String,
      messageId: String
  ): Request[Either[OpenAIException, MessageData]] =
    betaOpenAIAuthRequest
      .get(openAIUris.threadMessage(threadId, messageId))
      .response(asJson_parseErrors[MessageData])

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
  def modifyMessage(threadId: String, messageId: String, metadata: Map[String, String]): Request[Either[OpenAIException, MessageData]] =
    betaOpenAIAuthRequest
      .post(openAIUris.threadMessage(threadId, messageId))
      .body(metadata)
      .response(asJson_parseErrors[MessageData])

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
  def deleteMessage(threadId: String, messageId: String): Request[Either[OpenAIException, DeleteMessageResponse]] =
    betaOpenAIAuthRequest
      .delete(openAIUris.threadMessage(threadId, messageId))
      .response(asJson_parseErrors[DeleteMessageResponse])

  /** Create an assistant with a model and instructions.
    *
    * [[https://platform.openai.com/docs/api-reference/assistants/createAssistant]]
    *
    * @param createAssistantBody
    *   Create completion request body.
    */
  def createAssistant(createAssistantBody: CreateAssistantBody): Request[Either[OpenAIException, AssistantData]] =
    betaOpenAIAuthRequest
      .post(openAIUris.Assistants)
      .body(asJson(createAssistantBody))
      .response(asJson_parseErrors[AssistantData])

  /** Returns a list of assistants.
    *
    * [[https://platform.openai.com/docs/api-reference/assistants/listAssistants]]
    */
  def listAssistants(
      queryParameters: QueryParameters = QueryParameters.empty
  ): Request[Either[OpenAIException, ListAssistantsResponse]] = {
    val uri = openAIUris.Assistants
      .withParams(queryParameters.toMap)

    betaOpenAIAuthRequest
      .get(uri)
      .response(asJson_parseErrors[ListAssistantsResponse])
  }

  /** Retrieves an assistant.
    *
    * [[https://platform.openai.com/docs/api-reference/assistants/getAssistant]]
    *
    * @param assistantId
    *   The ID of the assistant to retrieve.
    */
  def retrieveAssistant(assistantId: String): Request[Either[OpenAIException, AssistantData]] =
    betaOpenAIAuthRequest
      .get(openAIUris.assistant(assistantId))
      .response(asJson_parseErrors[AssistantData])

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
  def modifyAssistant(assistantId: String, modifyAssistantBody: ModifyAssistantBody): Request[Either[OpenAIException, AssistantData]] =
    betaOpenAIAuthRequest
      .post(openAIUris.assistant(assistantId))
      .body(asJson(modifyAssistantBody))
      .response(asJson_parseErrors[AssistantData])

  /** Delete an assistant.
    *
    * [[https://platform.openai.com/docs/api-reference/assistants/deleteAssistant]]
    *
    * @param assistantId
    *   The ID of the assistant to delete.
    */
  def deleteAssistant(assistantId: String): Request[Either[OpenAIException, DeleteAssistantResponse]] =
    betaOpenAIAuthRequest
      .delete(openAIUris.assistant(assistantId))
      .response(asJson_parseErrors[DeleteAssistantResponse])

  /** Create a run.
    *
    * [[https://platform.openai.com/docs/api-reference/runs/createRun]]
    *
    * @param threadId
    *   The ID of the thread to run.
    * @param createRun
    *   Create run request body.
    */
  def createRun(threadId: String, createRun: CreateRun): Request[Either[OpenAIException, RunData]] =
    betaOpenAIAuthRequest
      .post(openAIUris.threadRuns(threadId))
      .body(asJson(createRun))
      .response(asJson_parseErrors[RunData])

  /** Create a thread and run it in one request.
    *
    * [[https://platform.openai.com/docs/api-reference/runs/createThreadAndRun]]
    *
    * @param createThreadAndRun
    *   Create thread and run request body.
    */
  def createThreadAndRun(createThreadAndRun: CreateThreadAndRun): Request[Either[OpenAIException, RunData]] =
    betaOpenAIAuthRequest
      .post(openAIUris.ThreadsRuns)
      .body(asJson(createThreadAndRun))
      .response(asJson_parseErrors[RunData])

  /** Returns a list of runs belonging to a thread..
    *
    * [[https://platform.openai.com/docs/api-reference/runs/listRuns]]
    *
    * @param threadId
    *   The ID of the thread the run belongs to.
    */
  def listRuns(threadId: String): Request[Either[OpenAIException, ListRunsResponse]] =
    betaOpenAIAuthRequest
      .get(openAIUris.threadRuns(threadId))
      .response(asJson_parseErrors[ListRunsResponse])

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
  ): Request[Either[OpenAIException, ListRunStepsResponse]] = {
    val uri = openAIUris
      .threadRunSteps(threadId, runId)
      .withParams(queryParameters.toMap)

    betaOpenAIAuthRequest
      .get(uri)
      .response(asJson_parseErrors[ListRunStepsResponse])
  }

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
  def retrieveRun(threadId: String, runId: String): Request[Either[OpenAIException, RunData]] =
    betaOpenAIAuthRequest
      .get(openAIUris.threadRun(threadId, runId))
      .response(asJson_parseErrors[RunData])

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
  def retrieveRunStep(threadId: String, runId: String, stepId: String): Request[Either[OpenAIException, RunStepData]] =
    betaOpenAIAuthRequest
      .get(openAIUris.threadRunStep(threadId, runId, stepId))
      .response(asJson_parseErrors[RunStepData])

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
  def modifyRun(threadId: String, runId: String, metadata: Map[String, String]): Request[Either[OpenAIException, RunData]] =
    betaOpenAIAuthRequest
      .post(openAIUris.threadRun(threadId, runId))
      .body(asJson(ModifyRun(metadata)))
      .response(asJson_parseErrors[RunData])

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
  def submitToolOutputs(threadId: String, runId: String, toolOutputs: Seq[ToolOutput]): Request[Either[OpenAIException, RunData]] =
    betaOpenAIAuthRequest
      .post(openAIUris.threadRunSubmitToolOutputs(threadId, runId))
      .body(asJson(SubmitToolOutputsToRun(toolOutputs)))
      .response(asJson_parseErrors[RunData])

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
  def cancelRun(threadId: String, runId: String): Request[Either[OpenAIException, RunData]] =
    betaOpenAIAuthRequest
      .post(openAIUris.threadRunCancel(threadId, runId))
      .response(asJson_parseErrors[RunData])

  /** Creates vector store
    *
    * @param createVectorStoreBody
    *   Options for new vector store
    * @return
    *   Newly created vector store or exception
    */
  def createVectorStore(createVectorStoreBody: CreateVectorStoreBody): Request[Either[OpenAIException, VectorStore]] =
    betaOpenAIAuthRequest
      .post(openAIUris.VectorStores)
      .body(asJson(createVectorStoreBody))
      .response(asJson_parseErrors[VectorStore])

  /** Lists vector store
    *
    * @param queryParameters
    *   Search params
    * @return
    *   List of vector stores matching criteria or exception
    */
  def listVectorStores(
      queryParameters: QueryParameters = QueryParameters.empty
  ): Request[Either[OpenAIException, ListVectorStoresResponse]] =
    betaOpenAIAuthRequest
      .get(openAIUris.VectorStores.withParams(queryParameters.toMap))
      .response(asJson_parseErrors[ListVectorStoresResponse])

  /** Retrieves vector store by id
    *
    * @param vectorStoreId
    *   Id of vector store
    * @return
    *   Vector store object or exception
    */
  def retrieveVectorStore(vectorStoreId: String): Request[Either[OpenAIException, VectorStore]] =
    betaOpenAIAuthRequest
      .get(openAIUris.vectorStore(vectorStoreId))
      .response(asJson_parseErrors[VectorStore])

  /** Modifies vector store
    *
    * @param vectorStoreId
    *   Id of vector store to modify
    * @param modifyVectorStoreBody
    *   New values for store properties
    * @return
    *   Modified vector store object
    */
  def modifyVectorStore(
      vectorStoreId: String,
      modifyVectorStoreBody: ModifyVectorStoreBody
  ): Request[Either[OpenAIException, VectorStore]] =
    betaOpenAIAuthRequest
      .post(openAIUris.vectorStore(vectorStoreId))
      .body(asJson(modifyVectorStoreBody))
      .response(asJson_parseErrors[VectorStore])

  /** Deletes vector store
    *
    * @param vectorStoreId
    *   Id of vector store to be deleted
    * @return
    *   Result of deleted operation
    */
  def deleteVectorStore(vectorStoreId: String): Request[Either[OpenAIException, DeleteVectorStoreResponse]] =
    betaOpenAIAuthRequest
      .delete(openAIUris.vectorStore(vectorStoreId))
      .response(asJson_parseErrors[DeleteVectorStoreResponse])

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
  ): Request[Either[OpenAIException, VectorStoreFile]] =
    betaOpenAIAuthRequest
      .post(openAIUris.vectorStoreFiles(vectorStoreId))
      .body(asJson(createVectorStoreFileBody))
      .response(asJson_parseErrors[VectorStoreFile])

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
  ): Request[Either[OpenAIException, ListVectorStoreFilesResponse]] =
    betaOpenAIAuthRequest
      .get(openAIUris.vectorStoreFiles(vectorStoreId).withParams(queryParameters.toMap))
      .response(asJson_parseErrors[ListVectorStoreFilesResponse])

  /** Retrieves vector store file by id
    *
    * @param vectorStoreId
    *   Id of vector store
    * @param fileId
    *   Id of vector store file
    * @return
    *   Vector store file
    */
  def retrieveVectorStoreFile(vectorStoreId: String, fileId: String): Request[Either[OpenAIException, VectorStoreFile]] =
    betaOpenAIAuthRequest
      .get(openAIUris.vectorStoreFile(vectorStoreId, fileId))
      .response(asJson_parseErrors[VectorStoreFile])

  /** Deletes vector store file by id
    *
    * @param vectorStoreId
    *   Id of vector store
    * @param fileId
    *   Id of vector store file
    * @return
    *   Result of delete operation
    */
  def deleteVectorStoreFile(vectorStoreId: String, fileId: String): Request[Either[OpenAIException, DeleteVectorStoreFileResponse]] =
    betaOpenAIAuthRequest
      .delete(openAIUris.vectorStoreFile(vectorStoreId, fileId))
      .response(asJson_parseErrors[DeleteVectorStoreFileResponse])

  /** Creates and executes a batch from an uploaded file of requests
    *
    * [[https://platform.openai.com/docs/api-reference/batch/create]]
    *
    * @param createBatchRequest
    *   Request body that will be used to create a batch.
    * @return
    *   The created Batch object.
    */
  def createBatch(createBatchRequest: BatchRequestBody): Request[Either[OpenAIException, BatchResponse]] =
    openAIAuthRequest
      .post(openAIUris.Batches)
      .body(asJson(createBatchRequest))
      .response(asJson_parseErrors[BatchResponse])

  /** Retrieves a batch.
    *
    * [[https://platform.openai.com/docs/api-reference/batch/retreive]]
    *
    * @param batchId
    *   The ID of the batch to retrieve.
    * @return
    *   The Batch object matching the specified ID.
    */
  def retrieveBatch(batchId: String): Request[Either[OpenAIException, BatchResponse]] =
    openAIAuthRequest
      .get(openAIUris.batch(batchId))
      .response(asJson_parseErrors[BatchResponse])

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
  def cancelBatch(batchId: String): Request[Either[OpenAIException, BatchResponse]] =
    openAIAuthRequest
      .post(openAIUris.cancelBatch(batchId))
      .response(asJson_parseErrors[BatchResponse])

  /** List your organization's batches.
    *
    * [[https://platform.openai.com/docs/api-reference/batch/list]]
    *
    * @return
    *   A list of paginated Batch objects.
    */
  def listBatches(
      queryParameters: batch.QueryParameters = batch.QueryParameters.empty
  ): Request[Either[OpenAIException, ListBatchResponse]] = {
    val uri = openAIUris.Batches
      .withParams(queryParameters.toMap)

    openAIAuthRequest
      .get(uri)
      .response(asJson_parseErrors[ListBatchResponse])
  }

  /** Create an organization admin API key
    *
    * [[https://platform.openai.com/docs/api-reference/admin-api-keys/create]]
    *
    * @param createAdminApiKeyRequest
    *   Request body that will be used to create an admin API key.
    * @return
    *   The created admin API key object.
    */
  def createAdminApiKey(createAdminApiKeyRequest: AdminApiKeyRequestBody): Request[Either[OpenAIException, AdminApiKeyResponse]] =
    openAIAuthRequest
      .post(openAIUris.AdminApiKeys)
      .body(asJson(createAdminApiKeyRequest))
      .response(asJson_parseErrors[AdminApiKeyResponse])

  /** Retrieve a single organization API key
    *
    * [[https://platform.openai.com/docs/api-reference/admin-api-keys/listget]]
    *
    * @param keyId
    *   Key id used to retrieve an admin API key.
    * @return
    *   The requested admin API key object.
    */
  def retrieveAdminApiKey(keyId: String): Request[Either[OpenAIException, AdminApiKeyResponse]] =
    openAIAuthRequest
      .get(openAIUris.adminApiKey(keyId))
      .response(asJson_parseErrors[AdminApiKeyResponse])

  /** List organization API keys
    *
    * [[https://platform.openai.com/docs/api-reference/admin-api-keys/list]]
    *
    * @return
    *   A list of admin API key objects.
    */
  def listAdminApiKeys(
      queryParameters: admin.QueryParameters = admin.QueryParameters.empty
  ): Request[Either[OpenAIException, ListAdminApiKeyResponse]] = {
    val uri = openAIUris.AdminApiKeys
      .withParams(queryParameters.toMap)

    openAIAuthRequest
      .get(uri)
      .response(asJson_parseErrors[ListAdminApiKeyResponse])
  }

  /** Delete an organization admin API key
    *
    * [[https://platform.openai.com/docs/api-reference/admin-api-keys/delete]]
    *
    * @param keyId
    *   Key id used to delete an admin API key.
    * @return
    *   A confirmation object indicating the key was deleted.
    */
  def deleteAdminApiKey(keyId: String): Request[Either[OpenAIException, DeleteAdminApiKeyResponse]] =
    openAIAuthRequest
      .delete(openAIUris.adminApiKey(keyId))
      .response(asJson_parseErrors[DeleteAdminApiKeyResponse])

  protected def openAIAuthRequest: PartialRequest[Either[String, String]] = basicRequest.auth
    .bearer(authToken)

  protected def betaOpenAIAuthRequest: PartialRequest[Either[String, String]] =
    openAIAuthRequest.withHeaders(openAIAuthRequest.headers :+ Header("OpenAI-Beta", "assistants=v2"))
}

private class OpenAIUris(val baseUri: Uri) {
  private val imageBase: Uri = uri"$baseUri/images"
  private val audioBase: Uri = uri"$baseUri/audio/"

  val ChatCompletions: Uri = uri"$baseUri/chat/completions"
  val Completions: Uri = uri"$baseUri/completions"
  val CreateImage: Uri = imageBase.addPath("generations")
  val Embeddings: Uri = uri"$baseUri/embeddings"
  val EditImage: Uri = imageBase.addPath("edits")
  val Files: Uri = uri"$baseUri/files"
  val Models: Uri = uri"$baseUri/models"
  val Moderations: Uri = uri"$baseUri/moderations"
  val FineTuningJobs: Uri = uri"$baseUri/fine_tuning/jobs"
  val Batches: Uri = uri"$baseUri/batches"
  val Uploads: Uri = uri"$baseUri/uploads"
  val AdminApiKeys: Uri = uri"$baseUri/organization/admin_api_keys"
  val Transcriptions: Uri = audioBase.addPath("transcriptions")
  val Translations: Uri = audioBase.addPath("translations")
  val Speech: Uri = audioBase.addPath("speech")
  val VariationsImage: Uri = imageBase.addPath("variations")
  val Responses: Uri = uri"$baseUri/responses"

  val Assistants: Uri = uri"$baseUri/assistants"
  val Threads: Uri = uri"$baseUri/threads"
  val ThreadsRuns: Uri = uri"$baseUri/threads/runs"
  val VectorStores: Uri = uri"$baseUri/vector_stores"

  def upload(uploadId: String): Uri = Uploads.addPath(uploadId)
  def uploadParts(uploadId: String): Uri = upload(uploadId).addPath("parts")
  def completeUpload(uploadId: String): Uri = upload(uploadId).addPath("complete")
  def cancelUpload(uploadId: String): Uri = upload(uploadId).addPath("cancel")

  def chatCompletion(completionId: String): Uri = ChatCompletions.addPath(completionId)
  def chatMessages(completionId: String): Uri = chatCompletion(completionId).addPath("messages")

  def fineTuningJob(fineTuningJobId: String): Uri = FineTuningJobs.addPath(fineTuningJobId)
  def fineTuningJobEvents(fineTuningJobId: String): Uri = fineTuningJob(fineTuningJobId).addPath("events")
  def fineTuningJobCheckpoints(fineTuningJobId: String): Uri = fineTuningJob(fineTuningJobId).addPath("checkpoints")
  def cancelFineTuningJob(fineTuningJobId: String): Uri = fineTuningJob(fineTuningJobId).addPath("cancel")

  def batch(batchId: String): Uri = Batches.addPath(batchId)
  def cancelBatch(batchId: String): Uri = batch(batchId).addPath("cancel")

  def adminApiKey(adminApiKeyId: String): Uri = AdminApiKeys.addPath(adminApiKeyId)

  def file(fileId: String): Uri = Files.addPath(fileId)
  def fileContent(fileId: String): Uri = Files.addPath(fileId, "content")
  def model(modelId: String): Uri = Models.addPath(modelId)

  def assistant(assistantId: String): Uri = Assistants.addPath(assistantId)

  def response(responseId: String): Uri = Responses.addPath(responseId)
  def cancelResponse(responseId: String): Uri = response(responseId).addPath("cancel")
  def responseInputItems(responseId: String): Uri = response(responseId).addPath("input_items")

  def thread(threadId: String): Uri = Threads.addPath(threadId)
  def threadMessages(threadId: String): Uri = Threads.addPath(threadId).addPath("messages")
  def threadMessage(threadId: String, messageId: String): Uri = Threads.addPath(threadId).addPath("messages").addPath(messageId)
  def threadRuns(threadId: String): Uri = Threads.addPath(threadId, "runs")
  def threadRun(threadId: String, runId: String): Uri = Threads.addPath(threadId, "runs", runId)

  def threadRunSteps(threadId: String, runId: String): Uri =
    Threads.addPath(threadId, "runs", runId, "steps")
  def threadRunStep(threadId: String, runId: String, stepId: String): Uri =
    Threads.addPath(threadId, "runs", runId, "steps", stepId)
  def threadRunCancel(threadId: String, runId: String): Uri =
    Threads.addPath(threadId, "runs", runId, "cancel")

  def threadRunSubmitToolOutputs(threadId: String, runId: String): Uri =
    Threads.addPath(threadId, "runs", runId, "submit_tool_outputs")

  def vectorStore(vectorStoreId: String): Uri =
    VectorStores.addPath(vectorStoreId)
  def vectorStoreFiles(vectorStoreId: String): Uri =
    vectorStore(vectorStoreId).addPath("files")
  def vectorStoreFile(vectorStoreId: String, fileId: String): Uri =
    vectorStoreFiles(vectorStoreId).addPath(fileId)
}

object OpenAIUris {
  val OpenAIBaseUri: Uri = uri"https://api.openai.com/v1"
}
