package sttp.ai.openai.json

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.extras.semiauto.{
  deriveConfiguredCodec,
  deriveConfiguredDecoder,
  deriveConfiguredEncoder,
  deriveEnumerationCodec,
  deriveEnumerationEncoder
}
import sttp.ai.core.json.CirceConfiguration.jsonConfiguration
import sttp.ai.core.json.CirceHelpers.{dropEmptyTopLevel, mergeExtraBody}
import sttp.ai.openai.requests.completions.{CompletionTokensDetails, PromptTokensDetails, Usage}
import sttp.ai.openai.requests.completions.chat.Audio
import sttp.ai.openai.requests.completions.chat.{ChatChunkRequestResponseData => Chunk, ChatRequestResponseData => Resp}
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatBody
import sttp.ai.openai.requests.completions.chat.{ChatRequestBody, Role}
import sttp.ai.openai.requests.completions.chat.message.Attachment
import sttp.ai.openai.requests.completions.chat.message.Message._
import sttp.ai.openai.requests.completions.chat.FunctionCall
import sttp.ai.openai.requests.completions.chat.message.{
  Content => MsgContent,
  Message,
  Tool => MsgTool,
  ToolChoice,
  ToolResource,
  ToolResources
}
import sttp.ai.openai.requests.vectorstore.ExpiresAfter
import sttp.ai.openai.requests.embeddings.{EmbeddingsRequestBody => EmbReq, EmbeddingsResponseBody => EmbResp}
import sttp.ai.openai.requests.moderations.{ModerationsRequestBody => ModReq, ModerationsResponseData => ModResp}
import sttp.ai.openai.requests.completions.{CompletionsRequestBody => CompReq, CompletionsResponseData => CompResp}
import sttp.ai.openai.requests.models.ModelsResponseData
import sttp.ai.openai.requests.files.FilesResponseData
import sttp.ai.openai.requests.audio.AudioResponseData
import sttp.ai.openai.requests.audio.speech.SpeechRequestBody
import sttp.ai.openai.requests.audio.speech.{ResponseFormat => SpeechResponseFormat, Voice => SpeechVoice}
import sttp.ai.openai.requests.images.ImageResponseData
import sttp.ai.openai.requests.images.creation.ImageCreationRequestBody
import sttp.ai.openai.requests.images.{ResponseFormat => ImageResponseFormat}
import sttp.ai.openai.requests.assistants.{
  AssistantsRequestBody,
  AssistantsResponseData,
  ReasoningEffort => AssistantsReasoningEffort,
  Tool => AssistantsTool
}
import sttp.ai.openai.requests.finetuning.{
  Dpo,
  Error => FtError,
  FineTuningJobCheckpointResponse,
  FineTuningJobEventResponse,
  FineTuningJobRequestBody,
  FineTuningJobResponse,
  Hyperparameters,
  Integration => FtIntegration,
  ListFineTuningJobCheckpointResponse,
  ListFineTuningJobEventResponse,
  ListFineTuningJobResponse,
  Method => FtMethod,
  Metrics,
  Status,
  Supervised,
  Type => FtType,
  Wandb
}
import sttp.ai.openai.requests.batch.{BatchRequestBody, BatchResponse, Data => BatchData, Errors, ListBatchResponse, RequestCounts}
import sttp.ai.openai.requests.upload.{CompleteUploadRequestBody, FileMetadata, UploadPartResponse, UploadRequestBody, UploadResponse}
import sttp.ai.openai.requests.admin.{
  AdminApiKeyRequestBody,
  AdminApiKeyResponse,
  DeleteAdminApiKeyResponse,
  ListAdminApiKeyResponse,
  Owner
}
import sttp.ai.openai.requests.vectorstore.{VectorStoreRequestBody, VectorStoreResponseData}
import sttp.ai.openai.requests.vectorstore.file.{FileStatus, VectorStoreFileRequestBody, VectorStoreFileResponseData}
import sttp.ai.openai.requests.threads.{ThreadsRequestBody, ThreadsResponseData}
import sttp.ai.openai.requests.threads.messages.{ThreadMessagesRequestBody, ThreadMessagesResponseData => TMR}
import sttp.ai.openai.requests.threads.runs.{ThreadRunsRequestBody, ThreadRunsResponseData => TRR}
import sttp.ai.openai.requests.responses.{Tool => RespTool}
import sttp.ai.openai.requests.responses.{ToolChoice => RespTC}
import sttp.ai.openai.requests.responses.{InputItemsListResponseBody => IIL}
import sttp.ai.openai.requests.responses.{ResponsesRequestBody => RRB}
import sttp.ai.openai.requests.responses.{ResponsesResponseBody => RRESP}
import sttp.ai.openai.requests.responses.DeleteModelResponseResponse
import sttp.ai.openai.requests.completions.chat.SchemaSupport.schemaCodec
import OpenAIManualCodecs._
import io.circe.generic.extras.encoding.ReprAsObjectEncoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.codec.{ConfiguredAsObjectCodec, ReprAsObjectCodec}
import io.circe.generic.extras.decoding.{ConfiguredDecoder, ReprDecoder}
import io.circe.generic.extras.encoding.ConfiguredAsObjectEncoder
import io.circe.syntax.{EncoderOps, KeyOps}
import shapeless._

/** Scala 2.13 configured (snake_case) codec registry for OpenAI case classes whose JSON keys differ from their Scala field names. Entries
  * are ordered leaf-first so nested implicits are initialized before the codecs that depend on them.
  */
object OpenAIDerivedCodecs {

  /** Decoder that ignores the `discriminator` field */
  def deriveAdtDecoder[A, R <: Coproduct](implicit gen: LabelledGeneric.Aux[A, R], codec: Lazy[ReprDecoder[R]]): Decoder[A] =
    ConfiguredDecoder
      .decodeAdt[A, R](gen, codec, jsonConfiguration.copy(discriminator = None))
      .or(ConfiguredDecoder.decodeAdt[A, R](gen, codec, jsonConfiguration))

  /** OpenAI API polymorphism is the equivalent of circe derivation without the discriminator with an additional "type" field
    * {"type":"function","function":"..."}
    */
  def deriveAdtEncoder[A, R <: Coproduct](implicit gen: LabelledGeneric.Aux[A, R], encoder: Lazy[ReprAsObjectEncoder[R]]): Encoder[A] =
    ConfiguredAsObjectEncoder.encodeAdt(gen, encoder, jsonConfiguration.copy(discriminator = None)).mapJsonObject { obj =>
      val (objType, objValue) = obj.toList.head
      objValue match {
        case json if json.asObject.exists(_.nonEmpty) => obj.+:("type" := objType)
        case _                                        => JsonObject("type" := objType)
      }
    }

  implicit val functionCallCodec: Codec[FunctionCall] = deriveConfiguredCodec
  implicit val imageUrlDetailsCodec: Codec[MsgContent.ImageUrlDetails] = deriveConfiguredCodec
  implicit val contentPartTextCodec: Codec[MsgContent.ContentPart.Text] = deriveConfiguredCodec
  implicit val contentPartImageUrlCodec: Codec[MsgContent.ContentPart.ImageUrl] = deriveConfiguredCodec
  implicit val msgContentPartCodec: Codec[MsgContent.ContentPart] = deriveConfiguredCodec
  implicit val msgContentCodec: Codec[MsgContent] = Codec.from(
    Decoder[String]
      .map(MsgContent.TextContent(_): MsgContent)
      .or(Decoder[Seq[MsgContent.ContentPart]].map(MsgContent.ArrayContent(_): MsgContent)),
    Encoder.instance {
      case MsgContent.TextContent(value)  => value.asJson
      case MsgContent.ArrayContent(value) => value.asJson
    }
  )
  implicit val expiresAfterCodec: Codec[ExpiresAfter] = deriveConfiguredCodec
  implicit val chatContentPartEncoder: Encoder[ChatRequestBody.ContentPart] = deriveConfiguredEncoder
  implicit val chatPredictionEncoder: Encoder[ChatRequestBody.Prediction] = deriveConfiguredEncoder
  implicit val chatStreamOptionsEncoder: Encoder[ChatRequestBody.StreamOptions] = deriveConfiguredEncoder
  implicit val updateChatCompletionRequestBodyEncoder: Encoder[ChatRequestBody.UpdateChatCompletionRequestBody] = deriveConfiguredEncoder

  // chatResponseFormatEncoder: Encoder[ChatRequestBody.ResponseFormat] -- hand-written in OpenAIManualCodecs so the `json_schema` case can
  // gate strict-mode schema normalization on the actual `strict` flag; see that file for rationale.

  // message Tool: OpenAI tagged form via deriveAdt*. function -> {"type":"function","function":{...}}, custom -> {"type":"custom","custom":{...}}.
  // The default `strict: false` on a function tool is omitted (matches the previous uPickle default-omission); an explicit `true` is kept.
  implicit val msgCustomFormatCodec: Codec[MsgTool.Custom.Format] = deriveConfiguredCodec
  implicit val msgToolEncoder: Encoder[MsgTool] = {
    // local import only: omit the default `strict: false` on a function tool, without affecting any other `Option[Boolean]` field
    import sttp.ai.core.json.CirceHelpers.omitFalse
    val base: Encoder[MsgTool] = deriveAdtEncoder
    base
  }
  implicit val msgToolDecoder: Decoder[MsgTool] = deriveAdtDecoder
  implicit val msgToolCodec: Codec[MsgTool] = Codec.from(msgToolDecoder, msgToolEncoder)

  // assistants Tool: OpenAI tagged form via deriveAdt*. function -> {"type":"function","function":{...}} (with `strict` omitted when None),
  // code_interpreter / file_search -> {"type":"..."}.
  implicit val assistantsToolEncoder: Encoder[AssistantsTool] = {
    val base: Encoder[AssistantsTool] = deriveAdtEncoder
    base.mapJson(_.deepDropNullValues)
  }
  implicit val assistantsToolDecoder: Decoder[AssistantsTool] = deriveAdtDecoder
  implicit val assistantsToolCodec: Codec[AssistantsTool] = Codec.from(assistantsToolDecoder, assistantsToolEncoder)

  implicit val codeInterpreterToolResourceCodec: Codec[ToolResource.CodeInterpreterToolResource] = deriveConfiguredCodec
  implicit val fileSearchToolResourceCodec: Codec[ToolResource.FileSearchToolResource] = deriveConfiguredCodec
  implicit val toolResourcesCodec: Codec[ToolResources] = deriveConfiguredCodec

  // message ToolChoice (encode-only): the bare-string modes (none / auto / required) can't go through a tagged object ADT
  // (OpenAI rejects `{"type":"auto"}`), so the dispatch emits them as strings and delegates the object cases (function / custom /
  // allowed_tools) to `deriveAdtEncoder`. The mode enum and `msgToolCodec` (for `AllowedTools.tools`) must be in scope.
  implicit val toolChoiceModeEncoder: Encoder[ToolChoice.AllowedTools.Mode] = deriveEnumerationEncoder[ToolChoice.AllowedTools.Mode]
  implicit val toolChoiceEncoder: Encoder[ToolChoice] = {
    val objectCases: Encoder[ToolChoice] = deriveAdtEncoder
    Encoder.instance {
      case ToolChoice.None     => Json.fromString("none")
      case ToolChoice.Auto     => Json.fromString("auto")
      case ToolChoice.Required => Json.fromString("required")
      case other               => objectCases(other)
    }
  }

  implicit val roleStandardCodec: Codec[Role.Standard] = deriveEnumerationCodec[Role.Standard]
  implicit val roleCodec: Codec[Role] = Codec.from(
    roleStandardCodec or Decoder.decodeString.map(Role.Custom(_)),
    Encoder.instance[Role] {
      case s: Role.Standard => roleStandardCodec(s)
      case Role.Custom(v)   => Json.fromString(v)
    }
  )

  implicit val assistantsReasoningEffortStandardEncoder: Encoder[AssistantsReasoningEffort.Standard] =
    deriveEnumerationEncoder[AssistantsReasoningEffort.Standard]
  implicit val assistantsReasoningEffortEncoder: Encoder[AssistantsReasoningEffort] = Encoder.instance[AssistantsReasoningEffort] {
    case s: AssistantsReasoningEffort.Standard              => assistantsReasoningEffortStandardEncoder(s)
    case AssistantsReasoningEffort.CustomReasoningEffort(v) => Json.fromString(v)
  }

  implicit val chatReasoningEffortStandardEncoder: Encoder[ChatRequestBody.ReasoningEffort.Standard] =
    deriveEnumerationEncoder[ChatRequestBody.ReasoningEffort.Standard]
  implicit val chatReasoningEffortEncoder: Encoder[ChatRequestBody.ReasoningEffort] = Encoder.instance[ChatRequestBody.ReasoningEffort] {
    case s: ChatRequestBody.ReasoningEffort.Standard              => chatReasoningEffortStandardEncoder(s)
    case ChatRequestBody.ReasoningEffort.CustomReasoningEffort(v) => Json.fromString(v)
  }

  implicit val chatVoiceStandardEncoder: Encoder[ChatRequestBody.Voice.Standard] = deriveEnumerationEncoder[ChatRequestBody.Voice.Standard]
  implicit val chatVoiceEncoder: Encoder[ChatRequestBody.Voice] = Encoder.instance[ChatRequestBody.Voice] {
    case s: ChatRequestBody.Voice.Standard    => chatVoiceStandardEncoder(s)
    case ChatRequestBody.Voice.CustomVoice(v) => Json.fromString(v)
  }

  implicit val chatFormatStandardEncoder: Encoder[ChatRequestBody.Format.Standard] =
    deriveEnumerationEncoder[ChatRequestBody.Format.Standard]
  implicit val chatFormatEncoder: Encoder[ChatRequestBody.Format] = Encoder.instance[ChatRequestBody.Format] {
    case s: ChatRequestBody.Format.Standard     => chatFormatStandardEncoder(s)
    case ChatRequestBody.Format.CustomFormat(v) => Json.fromString(v)
  }

  implicit val chatAudioEncoder: Encoder[ChatRequestBody.Audio] = deriveConfiguredEncoder

  implicit val speechVoiceStandardEncoder: Encoder[SpeechVoice.Standard] = deriveEnumerationEncoder[SpeechVoice.Standard]
  implicit val speechVoiceEncoder: Encoder[SpeechVoice] = Encoder.instance[SpeechVoice] {
    case s: SpeechVoice.Standard    => speechVoiceStandardEncoder(s)
    case SpeechVoice.CustomVoice(v) => Json.fromString(v)
  }

  implicit val speechResponseFormatStandardEncoder: Encoder[SpeechResponseFormat.Standard] =
    deriveEnumerationEncoder[SpeechResponseFormat.Standard]
  implicit val speechResponseFormatEncoder: Encoder[SpeechResponseFormat] = Encoder.instance[SpeechResponseFormat] {
    case s: SpeechResponseFormat.Standard     => speechResponseFormatStandardEncoder(s)
    case SpeechResponseFormat.CustomFormat(v) => Json.fromString(v)
  }

  implicit val imageResponseFormatStandardCodec: Codec[ImageResponseFormat.Standard] = deriveEnumerationCodec[ImageResponseFormat.Standard]
  implicit val imageResponseFormatCodec: Codec[ImageResponseFormat] = Codec.from(
    imageResponseFormatStandardCodec or Decoder.decodeString.map(ImageResponseFormat.Custom(_)),
    Encoder.instance[ImageResponseFormat] {
      case s: ImageResponseFormat.Standard => imageResponseFormatStandardCodec(s)
      case ImageResponseFormat.Custom(v)   => Json.fromString(v)
    }
  )

  // chat request messages
  implicit val systemMessageCodec: Codec[System] = deriveConfiguredCodec
  implicit val userMessageCodec: Codec[User] = deriveConfiguredCodec
  implicit val assistantMessageCodec: Codec[Assistant] = deriveConfiguredCodec
  implicit val toolMessageCodec: Codec[Tool] = deriveConfiguredCodec

  implicit val messageCodec: Codec[Message] = {
    implicit val jsonConfiguration: io.circe.generic.extras.Configuration =
      sttp.ai.core.json.CirceConfiguration.jsonConfiguration.withDiscriminator("role")
    Codec.from(deriveConfiguredDecoder[Message], deriveConfiguredEncoder[Message].mapJson(dropEmptyTopLevel))
  }

  implicit val chatBodyEncoder: Encoder[ChatBody] = deriveConfiguredEncoder[ChatBody].mapJson(mergeExtraBody("extra_body"))

  // usage
  implicit val completionTokensDetailsCodec: Codec[CompletionTokensDetails] = deriveConfiguredCodec
  implicit val promptTokensDetailsCodec: Codec[PromptTokensDetails] = deriveConfiguredCodec
  implicit val usageCodec: Codec[Usage] = deriveConfiguredCodec

  // chat completion response
  implicit val audioCodec: Codec[Audio] = deriveConfiguredCodec
  implicit val topLogprobsCodec: Codec[Resp.TopLogprobs] = deriveConfiguredCodec
  implicit val logprobDataCodec: Codec[Resp.LogprobData] = deriveConfiguredCodec
  implicit val logprobsCodec: Codec[Resp.Logprobs] = deriveConfiguredCodec
  implicit val chatRespMessageCodec: Codec[Resp.Message] = deriveConfiguredCodec
  implicit val chatRespChoicesCodec: Codec[Resp.Choices] = deriveConfiguredCodec
  implicit val chatResponseCodec: Codec[Resp.ChatResponse] = deriveConfiguredCodec
  implicit val listMessageResponseCodec: Codec[Resp.ListMessageResponse] = deriveConfiguredCodec
  implicit val listChatResponseCodec: Codec[Resp.ListChatResponse] = deriveConfiguredCodec
  implicit val deleteChatCompletionResponseCodec: Codec[Resp.DeleteChatCompletionResponse] = deriveConfiguredCodec

  // chat chunk (streaming) response
  implicit val chunkDeltaCodec: Codec[Chunk.Delta] = deriveConfiguredCodec
  implicit val chunkChoicesCodec: Codec[Chunk.Choices] = deriveConfiguredCodec
  implicit val chatChunkResponseCodec: Codec[Chunk.ChatChunkResponse] = deriveConfiguredCodec

  // request bodies
  implicit val embeddingsBodyEncoder: Encoder[EmbReq.EmbeddingsBody] =
    deriveConfiguredEncoder[EmbReq.EmbeddingsBody].mapJson(mergeExtraBody("extra_body"))
  implicit val moderationsBodyEncoder: Encoder[ModReq.ModerationsBody] = deriveConfiguredEncoder
  implicit val completionsBodyEncoder: Encoder[CompReq.CompletionsBody] =
    deriveConfiguredEncoder[CompReq.CompletionsBody].mapJson(mergeExtraBody("extra_body"))
  implicit val speechRequestBodyEncoder: Encoder[SpeechRequestBody] = deriveConfiguredEncoder

  // embeddings response
  implicit val embeddingDataCodec: Codec[EmbResp.EmbeddingData] = deriveConfiguredCodec
  implicit val embeddingsUsageCodec: Codec[EmbResp.Usage] = deriveConfiguredCodec
  implicit val embeddingResponseCodec: Codec[EmbResp.EmbeddingResponse] = deriveConfiguredCodec

  // moderations response
  implicit val categoryScoresCodec: Codec[ModResp.CategoryScores] = deriveConfiguredCodec
  implicit val categoriesCodec: Codec[ModResp.Categories] = deriveConfiguredCodec
  implicit val moderationResultCodec: Codec[ModResp.Result] = deriveConfiguredCodec
  implicit val moderationDataCodec: Codec[ModResp.ModerationData] = deriveConfiguredCodec

  // completions response
  implicit val completionsChoicesCodec: Codec[CompResp.Choices] = deriveConfiguredCodec
  implicit val completionsResponseCodec: Codec[CompResp.CompletionsResponse] = deriveConfiguredCodec

  // models response
  implicit val deletedModelDataCodec: Codec[ModelsResponseData.DeletedModelData] = deriveConfiguredCodec
  implicit val modelDataCodec: Codec[ModelsResponseData.ModelData] = deriveConfiguredCodec
  implicit val modelsResponseCodec: Codec[ModelsResponseData.ModelsResponse] = deriveConfiguredCodec

  // files response
  implicit val fileDataCodec: Codec[FilesResponseData.FileData] = deriveConfiguredCodec
  implicit val filesResponseCodec: Codec[FilesResponseData.FilesResponse] = deriveConfiguredCodec
  implicit val deletedFileDataCodec: Codec[FilesResponseData.DeletedFileData] = deriveConfiguredCodec

  // audio response
  implicit val audioResponseCodec: Codec[AudioResponseData.AudioResponse] = deriveConfiguredCodec

  // images
  implicit val generatedImageDataCodec: Codec[ImageResponseData.GeneratedImageData] = deriveConfiguredCodec
  implicit val imageResponseCodec: Codec[ImageResponseData.ImageResponse] = deriveConfiguredCodec
  implicit val imageCreationBodyEncoder: Encoder[ImageCreationRequestBody.ImageCreationBody] = deriveConfiguredEncoder

  // assistants
  implicit val createAssistantBodyEncoder: Encoder[AssistantsRequestBody.CreateAssistantBody] =
    deriveConfiguredEncoder[AssistantsRequestBody.CreateAssistantBody].mapJson(dropEmptyTopLevel)
  implicit val modifyAssistantBodyEncoder: Encoder[AssistantsRequestBody.ModifyAssistantBody] =
    deriveConfiguredEncoder[AssistantsRequestBody.ModifyAssistantBody].mapJson(dropEmptyTopLevel)
  implicit val assistantDataCodec: Codec[AssistantsResponseData.AssistantData] = deriveConfiguredCodec
  implicit val listAssistantsResponseCodec: Codec[AssistantsResponseData.ListAssistantsResponse] = deriveConfiguredCodec
  implicit val deleteAssistantResponseCodec: Codec[AssistantsResponseData.DeleteAssistantResponse] = deriveConfiguredCodec

  // finetuning
  implicit val finetuningTypeCodec: Codec[FtType] =
    OpenAIManualCodecs.typeCodec(Map("wandb" -> FtIntegration.Wandb, "supervised" -> FtMethod.Supervised, "dpo" -> FtMethod.Dpo))

  implicit val hyperparametersCodec: Codec[Hyperparameters] = deriveConfiguredCodec
  implicit val wandbCodec: Codec[Wandb] = deriveConfiguredCodec
  implicit val ftIntegrationCodec: Codec[FtIntegration] = deriveConfiguredCodec
  implicit val supervisedCodec: Codec[Supervised] = deriveConfiguredCodec
  implicit val dpoCodec: Codec[Dpo] = deriveConfiguredCodec
  implicit val ftMethodCodec: Codec[FtMethod] = deriveConfiguredCodec
  implicit val ftErrorCodec: Codec[FtError] = deriveConfiguredCodec
  implicit val metricsCodec: Codec[Metrics] = deriveConfiguredCodec
  implicit val standardStatusCodec: Codec[Status.Standard] = deriveEnumerationCodec[Status.Standard]
  implicit val fineTuningStatusCodec: Codec[Status] = Codec.from(
    standardStatusCodec or Decoder.decodeString.map(Status.Custom(_)),
    Encoder.instance[Status] {
      case s: Status.Standard    => standardStatusCodec(s)
      case Status.Custom(custom) => custom.asJson
    }
  )
  implicit val fineTuningJobResponseCodec: Codec[FineTuningJobResponse] = deriveConfiguredCodec
  implicit val listFineTuningJobResponseCodec: Codec[ListFineTuningJobResponse] = deriveConfiguredCodec
  implicit val fineTuningJobEventResponseDecoder: Decoder[FineTuningJobEventResponse] = deriveConfiguredDecoder
  implicit val listFineTuningJobEventResponseCodec: Decoder[ListFineTuningJobEventResponse] = deriveConfiguredDecoder
  implicit val fineTuningJobCheckpointResponseCodec: Codec[FineTuningJobCheckpointResponse] = deriveConfiguredCodec
  implicit val listFineTuningJobCheckpointResponseCodec: Codec[ListFineTuningJobCheckpointResponse] = deriveConfiguredCodec
  implicit val fineTuningJobRequestBodyEncoder: Encoder[FineTuningJobRequestBody] = deriveConfiguredEncoder

  // batch
  implicit val batchRequestBodyEncoder: Encoder[BatchRequestBody] = deriveConfiguredEncoder
  implicit val batchDataCodec: Codec[BatchData] = deriveConfiguredCodec
  implicit val errorsCodec: Codec[Errors] = deriveConfiguredCodec
  implicit val requestCountsCodec: Codec[RequestCounts] = deriveConfiguredCodec
  implicit val batchResponseCodec: Codec[BatchResponse] = deriveConfiguredCodec
  implicit val listBatchResponseCodec: Codec[ListBatchResponse] = deriveConfiguredCodec

  // upload
  implicit val uploadRequestBodyEncoder: Encoder[UploadRequestBody] = deriveConfiguredEncoder
  implicit val completeUploadRequestBodyEncoder: Encoder[CompleteUploadRequestBody] = deriveConfiguredEncoder
  implicit val fileMetadataCodec: Codec[FileMetadata] = deriveConfiguredCodec
  implicit val uploadResponseCodec: Codec[UploadResponse] = deriveConfiguredCodec
  implicit val uploadPartResponseCodec: Codec[UploadPartResponse] = deriveConfiguredCodec

  // admin
  implicit val adminApiKeyRequestBodyEncoder: Encoder[AdminApiKeyRequestBody] = deriveConfiguredEncoder
  implicit val ownerCodec: Codec[Owner] = deriveConfiguredCodec
  implicit val adminApiKeyResponseCodec: Codec[AdminApiKeyResponse] = deriveConfiguredCodec
  implicit val listAdminApiKeyResponseCodec: Codec[ListAdminApiKeyResponse] = deriveConfiguredCodec
  implicit val deleteAdminApiKeyResponseCodec: Codec[DeleteAdminApiKeyResponse] = deriveConfiguredCodec

  // vectorstore (enumeration codecs: case-object enums encoded as snake_case strings)
  implicit val fileStatusCodec: Codec[FileStatus] = deriveEnumerationCodec
  implicit val storeStatusCodec: Codec[VectorStoreResponseData.StoreStatus] = deriveEnumerationCodec[VectorStoreResponseData.StoreStatus]
  implicit val errorCodeCodec: Codec[VectorStoreFileResponseData.ErrorCode] = deriveEnumerationCodec[VectorStoreFileResponseData.ErrorCode]

  implicit val createVectorStoreBodyEncoder: Encoder[VectorStoreRequestBody.CreateVectorStoreBody] = deriveConfiguredEncoder
  implicit val modifyVectorStoreBodyEncoder: Encoder[VectorStoreRequestBody.ModifyVectorStoreBody] = deriveConfiguredEncoder
  implicit val fileCountsCodec: Codec[VectorStoreResponseData.FileCounts] = deriveConfiguredCodec
  implicit val vectorStoreCodec: Codec[VectorStoreResponseData.VectorStore] = deriveConfiguredCodec
  implicit val listVectorStoresResponseCodec: Codec[VectorStoreResponseData.ListVectorStoresResponse] = deriveConfiguredCodec
  implicit val deleteVectorStoreResponseCodec: Codec[VectorStoreResponseData.DeleteVectorStoreResponse] = deriveConfiguredCodec
  implicit val createVectorStoreFileBodyEncoder: Encoder[VectorStoreFileRequestBody.CreateVectorStoreFileBody] = deriveConfiguredEncoder
  implicit val listVectorStoreFilesBodyEncoder: Encoder[VectorStoreFileRequestBody.ListVectorStoreFilesBody] = deriveConfiguredEncoder
  implicit val lastErrorCodec: Codec[VectorStoreFileResponseData.LastError] = deriveConfiguredCodec
  implicit val vectorStoreFileCodec: Codec[VectorStoreFileResponseData.VectorStoreFile] = deriveConfiguredCodec
  implicit val listVectorStoreFilesResponseCodec: Codec[VectorStoreFileResponseData.ListVectorStoreFilesResponse] = deriveConfiguredCodec
  implicit val deleteVectorStoreFileResponseCodec: Codec[VectorStoreFileResponseData.DeleteVectorStoreFileResponse] = deriveConfiguredCodec

  // threads request bodies
  implicit val attachmentCodec: Codec[Attachment] = {
    import sttp.ai.core.json.CirceHelpers.emptyIterableAsNone // local: empty `tools` array -> None
    deriveConfiguredCodec
  }
  implicit val createMessageEncoder: Encoder[ThreadMessagesRequestBody.CreateMessage] = deriveConfiguredEncoder
  implicit val createThreadBodyEncoder: Encoder[ThreadsRequestBody.CreateThreadBody] = deriveConfiguredEncoder
  implicit val toolOutputEncoder: Encoder[ThreadRunsRequestBody.ToolOutput] = deriveConfiguredEncoder
  implicit val submitToolOutputsToRunEncoder: Encoder[ThreadRunsRequestBody.SubmitToolOutputsToRun] = deriveConfiguredEncoder
  implicit val modifyRunEncoder: Encoder[ThreadRunsRequestBody.ModifyRun] =
    deriveConfiguredEncoder[ThreadRunsRequestBody.ModifyRun].mapJson(dropEmptyTopLevel)
  implicit val createRunEncoder: Encoder[ThreadRunsRequestBody.CreateRun] =
    deriveConfiguredEncoder[ThreadRunsRequestBody.CreateRun].mapJson(dropEmptyTopLevel)
  implicit val createThreadAndRunEncoder: Encoder[ThreadRunsRequestBody.CreateThreadAndRun] =
    deriveConfiguredEncoder[ThreadRunsRequestBody.CreateThreadAndRun].mapJson(dropEmptyTopLevel)
  // threads response
  implicit val threadDataCodec: Codec[ThreadsResponseData.ThreadData] = deriveConfiguredCodec
  implicit val deleteThreadResponseCodec: Codec[ThreadsResponseData.DeleteThreadResponse] = deriveConfiguredCodec

  // thread messages response (decode-only). Leaf-first: details, then the annotation/content subtypes, then the dispatch decoders.
  implicit val fileCitationDetailsDecoder: Decoder[TMR.Annotation.FileCitationDetails] = deriveConfiguredDecoder
  implicit val filePathDetailsDecoder: Decoder[TMR.Annotation.FilePathDetails] = deriveConfiguredDecoder
  implicit val fileCitationDecoder: Decoder[TMR.Annotation.FileCitation] = deriveConfiguredDecoder
  implicit val filePathDecoder: Decoder[TMR.Annotation.FilePath] = deriveConfiguredDecoder
  // flat `"type"` discriminator matches the snake_case constructor names (file_citation / file_path)
  implicit val tmrAnnotationDecoder: Decoder[TMR.Annotation] = deriveConfiguredDecoder
  implicit val textContentValueDecoder: Decoder[TMR.Content.TextContentValue] = deriveConfiguredDecoder
  implicit val textDecoder: Decoder[TMR.Content.Text] = deriveConfiguredDecoder
  implicit val imageFileDetailsDecoder: Decoder[TMR.Content.ImageFileDetails] = deriveConfiguredDecoder
  implicit val imageFileDecoder: Decoder[TMR.Content.ImageFile] = deriveConfiguredDecoder
  // flat `"type"` discriminator matches the snake_case constructor names (text / image_file)
  implicit val tmrContentDecoder: Decoder[TMR.Content] = deriveConfiguredDecoder
  implicit val messageDataDecoder: Decoder[TMR.MessageData] =
    deriveConfiguredDecoder[TMR.MessageData].prepare(_.withFocus(dropEmptyTopLevel))
  implicit val listMessagesResponseDecoder: Decoder[TMR.ListMessagesResponse] = deriveConfiguredDecoder
  implicit val deleteMessageResponseDecoder: Decoder[TMR.DeleteMessageResponse] = deriveConfiguredDecoder

  // thread runs response (decode-only; RequiredAction/Error/StepDetails/ToolCall are dispatch decoders)
  implicit val runsUsageDecoder: Decoder[TRR.Usage] = deriveConfiguredDecoder
  implicit val functionCallResultDecoder: Decoder[TRR.ToolCall.FunctionCallResult] = deriveConfiguredDecoder
  implicit val fileSearchResultContentDecoder: Decoder[TRR.ToolCall.FileSearch.FileSearchDetails.FileSearchResult.Content] =
    deriveConfiguredDecoder
  implicit val fileSearchResultDecoder: Decoder[TRR.ToolCall.FileSearch.FileSearchDetails.FileSearchResult] = deriveConfiguredDecoder
  implicit val rankingOptionsDecoder: Decoder[TRR.ToolCall.FileSearch.FileSearchDetails.RankingOptions] = deriveConfiguredDecoder
  implicit val fileSearchInnerDecoder: Decoder[TRR.ToolCall.FileSearch.FileSearchDetails] = deriveConfiguredDecoder
  implicit val codeInterpreterDecoder: Decoder[TRR.ToolCall.CodeInterpreter] = deriveConfiguredDecoder
  implicit val fileSearchDecoder: Decoder[TRR.ToolCall.FileSearch] = deriveConfiguredDecoder
  implicit val functionDecoder: Decoder[TRR.ToolCall.Function] = deriveConfiguredDecoder
  // flat `"type"` discriminator matches the snake_case constructor names (code_interpreter / file_search / function), so a configured ADT
  // derivation replaces the hand-written dispatch that previously lived in OpenAIManualCodecs.
  implicit val trrToolCallDecoder: Decoder[TRR.ToolCall] = deriveConfiguredDecoder
  implicit val toolCallsDecoder: Decoder[TRR.ToolCalls] = deriveConfiguredDecoder
  implicit val messageCreationDecoder: Decoder[TRR.MessageCreation] = deriveConfiguredDecoder
  // dispatch on `"type"`: `tool_calls` decodes the flat object, while `message_creation` pulls its payload from the nested
  // `message_creation` field (so the two branches have different shapes and this can't be a uniform configured ADT).
  implicit val trrStepDetailsDecoder: Decoder[TRR.StepDetails] = Decoder.instance { c =>
    c.get[String]("type").flatMap {
      case "message_creation" => messageCreationDecoder.at("message_creation")(c)
      case "tool_calls"       => c.as[TRR.ToolCalls]
      case other              => Left(DecodingFailure(s"Unknown step details type: $other", c.history))
    }
  }
  implicit val submitToolOutputsDecoder: Decoder[TRR.SubmitToolOutputs] = deriveConfiguredDecoder
  implicit val submitToolOutputsRequiredActionDecoder: Decoder[TRR.RequiredAction.SubmitToolOutputs] = deriveConfiguredDecoder
  // configured ADT on the default `"type"` discriminator: the single subtype's snake_case name (`submit_tool_outputs`) matches the
  // discriminator value. Replaces the hand-written dispatch that previously lived in OpenAIManualCodecs.
  implicit val trrRequiredActionDecoder: Decoder[TRR.RequiredAction] = deriveConfiguredDecoder
  implicit val serverErrorDecoder: Decoder[TRR.Error.ServerError] = deriveConfiguredDecoder
  implicit val rateLimitExceededDecoder: Decoder[TRR.Error.RateLimitExceeded] = deriveConfiguredDecoder
  implicit val invalidPromptDecoder: Decoder[TRR.Error.InvalidPrompt] = deriveConfiguredDecoder
  // ADT dispatch on `"code"` (not the default `"type"`): the snake_case constructor names match the discriminator values
  // (server_error / rate_limit_exceeded / invalid_prompt). Replaces the hand-written dispatch that previously lived in OpenAIManualCodecs.
  implicit val trrErrorDecoder: Decoder[TRR.Error] = {
    // shadow the file-level `jsonConfiguration` so only this `"code"`-discriminated config is in implicit scope here
    implicit val jsonConfiguration: io.circe.generic.extras.Configuration =
      sttp.ai.core.json.CirceConfiguration.jsonConfiguration.withDiscriminator("code")
    deriveConfiguredDecoder[TRR.Error]
  }
  implicit val runDataDecoder: Decoder[TRR.RunData] = deriveConfiguredDecoder
  implicit val listRunsResponseDecoder: Decoder[TRR.ListRunsResponse] = deriveConfiguredDecoder
  implicit val runStepDataDecoder: Decoder[TRR.RunStepData] = deriveConfiguredDecoder
  implicit val listRunStepsResponseDecoder: Decoder[TRR.ListRunStepsResponse] = deriveConfiguredDecoder

  // responses Tool (request, encode-only) leaf encoders
  implicit val respUserLocationEncoder: Codec[RespTool.UserLocation] = deriveConfiguredCodec
  implicit val respFunctionEncoder: Codec[RespTool.Function] = deriveConfiguredCodec
  implicit val respFsMetadataEncoder: Codec[RespTool.FileSearch.Filter.Metadata] = deriveConfiguredCodec
  implicit val respFsFileIdsEncoder: Codec[RespTool.FileSearch.Filter.FileIds] = deriveConfiguredCodec
  implicit val respFsFilterCodec: Codec[RespTool.FileSearch.Filter] = deriveConfiguredCodec
  implicit val respRankingOptionsEncoder: Codec[RespTool.FileSearch.RankingOptions] = deriveConfiguredCodec
  implicit val respFileSearchEncoder: Codec[RespTool.FileSearch] = deriveConfiguredCodec
  implicit val respDefaultWebSearchPreviewEncoder: Codec[RespTool.WebSearchPreview.DefaultWebSearchPreview] = deriveConfiguredCodec
  implicit val respWebSearchPreview20250311Encoder: Codec[RespTool.WebSearchPreview.WebSearchPreview20250311] = deriveConfiguredCodec
  implicit val respComputerUsePreviewEncoder: Codec[RespTool.ComputerUsePreview] = deriveConfiguredCodec
  implicit val respApprovalAlwaysEncoder: Codec[RespTool.Mcp.ApprovalFilter.Always] = deriveConfiguredCodec
  implicit val respApprovalNeverEncoder: Codec[RespTool.Mcp.ApprovalFilter.Never] = deriveConfiguredCodec
  implicit val respRequireApprovalFilterEncoder: Codec[RespTool.Mcp.RequireApproval.Filter] = deriveConfiguredCodec
  implicit val respMcpEncoder: Codec[RespTool.Mcp] = deriveConfiguredCodec
  implicit val respContainerAutoEncoder: Codec[RespTool.CodeInterpreter.Container.ContainerAuto] = deriveConfiguredCodec
  implicit val respCodeInterpreterEncoder: Codec[RespTool.CodeInterpreter] = deriveConfiguredCodec
  implicit val respInputImageMaskEncoder: Codec[RespTool.ImageGeneration.InputImageMask] = deriveConfiguredCodec
  implicit val respImageGenerationEncoder: Codec[RespTool.ImageGeneration] = deriveConfiguredCodec
  implicit val respGrammarEncoder: Codec[RespTool.Custom.Format.Grammar] = deriveConfiguredCodec
  implicit val respCustomFormatCodec: Codec[RespTool.Custom.Format] = deriveConfiguredCodec
  implicit val respCustomEncoder: Codec[RespTool.Custom] = deriveConfiguredCodec
  implicit val respLocalShellEncoder: Codec[RespTool.LocalShell] = deriveConfiguredCodec

  // responses ToolChoice (request, encode-only) leaf encoders
  // enumeration codec: the case-object names snake_case to the JSON strings (none / auto / required)
  implicit val respToolChoiceModeCodec: Codec[RespTC.ToolChoiceMode] = deriveEnumerationCodec[RespTC.ToolChoiceMode]
  implicit val tcToolDefFunctionEncoder: Codec[RespTC.ToolChoiceObject.AllowedTools.ToolDefinition.Function] = deriveConfiguredCodec
  implicit val tcToolDefMcpEncoder: Codec[RespTC.ToolChoiceObject.AllowedTools.ToolDefinition.Mcp] = deriveConfiguredCodec
  implicit val respToolDefinitionCodec: Codec[RespTC.ToolChoiceObject.AllowedTools.ToolDefinition] = deriveConfiguredCodec
  implicit val tcAllowedToolsEncoder: Codec[RespTC.ToolChoiceObject.AllowedTools] = deriveConfiguredCodec
  implicit val tcFunctionEncoder: Codec[RespTC.ToolChoiceObject.Function] = deriveConfiguredCodec
  implicit val tcMcpEncoder: Codec[RespTC.ToolChoiceObject.Mcp] = deriveConfiguredCodec
  implicit val tcCustomEncoder: Codec[RespTC.ToolChoiceObject.Custom] = deriveConfiguredCodec
  implicit val respToolChoiceObjectCodec: Codec[RespTC.ToolChoiceObject] = deriveConfiguredCodec

  // responses InputItemsListResponseBody (response, decode-only)
  implicit val iiTopLogProbDecoder: Decoder[IIL.InputItem.OutputContent.TopLogProb] = deriveConfiguredDecoder
  implicit val iiLogProbDecoder: Decoder[IIL.InputItem.OutputContent.LogProb] = deriveConfiguredDecoder
  implicit val iiFileSearchResultDecoder: Decoder[IIL.InputItem.FileSearchResult] = deriveConfiguredDecoder
  implicit val iiPendingSafetyCheckDecoder: Decoder[IIL.InputItem.ComputerCall.PendingSafetyCheck] = deriveConfiguredDecoder
  implicit val iiComputerScreenshotDecoder: Decoder[IIL.InputItem.ComputerCallOutput.ComputerScreenshot] = deriveConfiguredDecoder
  implicit val iiAcknowledgedSafetyCheckDecoder: Decoder[IIL.InputItem.ComputerCallOutput.AcknowledgedSafetyCheck] =
    deriveConfiguredDecoder
  implicit val iiLocalShellActionDecoder: Decoder[IIL.InputItem.LocalShellCall.Action] = deriveConfiguredDecoder
  implicit val iiMcpListToolsToolDecoder: Decoder[IIL.InputItem.McpListTools.Tool] = deriveConfiguredDecoder
  implicit val iiInputContentDecoder: Decoder[IIL.InputItem.InputContent] = deriveConfiguredDecoder
  implicit val iiAnnotationDecoder: Decoder[IIL.InputItem.OutputContent.Annotation] = deriveConfiguredDecoder
  implicit val iiOutputContentDecoder: Decoder[IIL.InputItem.OutputContent] = deriveConfiguredDecoder
  implicit val iiComputerActionDecoder: Decoder[IIL.InputItem.ComputerCall.Action] = deriveConfiguredDecoder
  implicit val iiWebActionDecoder: Decoder[IIL.InputItem.WebSearchCall.Action] = deriveConfiguredDecoder
  implicit val iiCodeOutputDecoder: Decoder[IIL.InputItem.CodeInterpreterCall.Output] = deriveConfiguredDecoder
  implicit val iiInputMessageDecoder: Decoder[IIL.InputItem.InputMessage] = deriveConfiguredDecoder
  implicit val iiOutputMessageDecoder: Decoder[IIL.InputItem.OutputMessage] = deriveConfiguredDecoder
  implicit val iiFileSearchCallDecoder: Decoder[IIL.InputItem.FileSearchCall] = deriveConfiguredDecoder
  implicit val iiComputerCallDecoder: Decoder[IIL.InputItem.ComputerCall] = deriveConfiguredDecoder
  implicit val iiComputerCallOutputDecoder: Decoder[IIL.InputItem.ComputerCallOutput] = deriveConfiguredDecoder
  implicit val iiWebSearchCallDecoder: Decoder[IIL.InputItem.WebSearchCall] = deriveConfiguredDecoder
  implicit val iiFunctionCallDecoder: Decoder[IIL.InputItem.FunctionCall] = deriveConfiguredDecoder
  implicit val iiFunctionCallOutputDecoder: Decoder[IIL.InputItem.FunctionCallOutput] = deriveConfiguredDecoder
  implicit val iiImageGenerationCallDecoder: Decoder[IIL.InputItem.ImageGenerationCall] = deriveConfiguredDecoder
  implicit val iiCodeInterpreterCallDecoder: Decoder[IIL.InputItem.CodeInterpreterCall] = deriveConfiguredDecoder
  implicit val iiLocalShellCallDecoder: Decoder[IIL.InputItem.LocalShellCall] = deriveConfiguredDecoder
  implicit val iiLocalShellCallOutputDecoder: Decoder[IIL.InputItem.LocalShellCallOutput] = deriveConfiguredDecoder
  implicit val iiMcpListToolsDecoder: Decoder[IIL.InputItem.McpListTools] = deriveConfiguredDecoder
  implicit val iiMcpApprovalRequestDecoder: Decoder[IIL.InputItem.McpApprovalRequest] = deriveConfiguredDecoder
  implicit val iiMcpApprovalResponseDecoder: Decoder[IIL.InputItem.McpApprovalResponse] = deriveConfiguredDecoder
  implicit val iiMcpToolCallDecoder: Decoder[IIL.InputItem.McpToolCall] = deriveConfiguredDecoder
  // flat `"type"` discriminator matches the snake_case constructor names. The `message` branch is handled explicitly: shapeless flattens the
  // nested `Message` sealed sub-trait into its `input_message` / `output_message` leaves, so the derived decoder has no `message` member and
  // would fail with "decoding to CNil"; we intercept `"type":"message"` and delegate to `iilMessageDecoder` (OpenAIManualCodecs), which
  // distinguishes input/output messages structurally. (Scala 3's `Mirror` keeps `Message` as a member, so it needs no such interception.)
  implicit val iilInputItemDecoder: Decoder[IIL.InputItem] = {
    val derived: Decoder[IIL.InputItem] = deriveConfiguredDecoder
    Decoder.instance { c =>
      c.get[String]("type").flatMap {
        case "message" => iilMessageDecoder(c).map(m => m: IIL.InputItem)
        case _         => derived(c)
      }
    }
  }
  implicit val inputItemsListResponseBodyDecoder: Decoder[IIL] = deriveConfiguredDecoder

  // responses ResponsesRequestBody (request, encode-only)
  implicit val rrTopLogProbEncoder: Encoder[RRB.Input.OutputContentItem.OutputText.TopLogProb] = deriveConfiguredEncoder
  implicit val rrLogProbEncoder: Encoder[RRB.Input.OutputContentItem.OutputText.LogProb] = deriveConfiguredEncoder
  implicit val rrFileSearchResultEncoder: Encoder[RRB.Input.FileSearchCall.FileSearchResult] = deriveConfiguredEncoder
  implicit val rrPendingSafetyCheckEncoder: Encoder[RRB.Input.ComputerCall.PendingSafetyCheck] = deriveConfiguredEncoder
  implicit val rrComputerScreenshotEncoder: Encoder[RRB.Input.ComputerCallOutput.ComputerScreenshot] = deriveConfiguredEncoder
  implicit val rrAcknowledgedSafetyCheckEncoder: Encoder[RRB.Input.ComputerCallOutput.AcknowledgedSafetyCheck] = deriveConfiguredEncoder
  implicit val rrSummaryTextEncoder: Encoder[RRB.Input.Reasoning.SummaryText] = deriveConfiguredEncoder
  implicit val rrLocalShellActionEncoder: Encoder[RRB.Input.LocalShellCall.Action] = deriveConfiguredEncoder
  implicit val rrMcpListToolsToolEncoder: Encoder[RRB.Input.McpListTools.Tool] = deriveConfiguredEncoder
  implicit val rrInputContentItemEncoder: Encoder[RRB.Input.InputContentItem] = deriveConfiguredEncoder
  implicit val rrAnnotationEncoder: Encoder[RRB.Input.OutputContentItem.OutputText.Annotation] = deriveConfiguredEncoder
  implicit val rrOutputContentItemEncoder: Encoder[RRB.Input.OutputContentItem] = deriveConfiguredEncoder
  implicit val rrComputerActionEncoder: Encoder[RRB.Input.ComputerCall.Action] = deriveConfiguredEncoder
  implicit val rrWebActionEncoder: Encoder[RRB.Input.WebSearchCall.Action] = deriveConfiguredEncoder
  implicit val rrCodeOutputEncoder: Encoder[RRB.Input.CodeInterpreterCall.Output] = deriveConfiguredEncoder
  implicit val rrInputMessageEncoder: Encoder[RRB.Input.InputMessage] = deriveConfiguredEncoder
  implicit val rrOutputMessageEncoder: Encoder[RRB.Input.OutputMessage] = deriveConfiguredEncoder
  implicit val rrFileSearchCallEncoder: Encoder[RRB.Input.FileSearchCall] = deriveConfiguredEncoder
  implicit val rrComputerCallEncoder: Encoder[RRB.Input.ComputerCall] = deriveConfiguredEncoder
  implicit val rrComputerCallOutputEncoder: Encoder[RRB.Input.ComputerCallOutput] = deriveConfiguredEncoder
  implicit val rrWebSearchCallEncoder: Encoder[RRB.Input.WebSearchCall] = deriveConfiguredEncoder
  implicit val rrFunctionCallEncoder: Encoder[RRB.Input.FunctionCall] = deriveConfiguredEncoder
  implicit val rrFunctionCallOutputEncoder: Encoder[RRB.Input.FunctionCallOutput] = deriveConfiguredEncoder
  implicit val rrReasoningEncoder: Encoder[RRB.Input.Reasoning] = deriveConfiguredEncoder
  implicit val rrImageGenerationCallEncoder: Encoder[RRB.Input.ImageGenerationCall] = deriveConfiguredEncoder
  implicit val rrCodeInterpreterCallEncoder: Encoder[RRB.Input.CodeInterpreterCall] = deriveConfiguredEncoder
  implicit val rrLocalShellCallEncoder: Encoder[RRB.Input.LocalShellCall] = deriveConfiguredEncoder
  implicit val rrLocalShellCallOutputEncoder: Encoder[RRB.Input.LocalShellCallOutput] = deriveConfiguredEncoder
  implicit val rrMcpListToolsEncoder: Encoder[RRB.Input.McpListTools] = deriveConfiguredEncoder
  implicit val rrMcpApprovalRequestEncoder: Encoder[RRB.Input.McpApprovalRequest] = deriveConfiguredEncoder
  implicit val rrMcpApprovalResponseEncoder: Encoder[RRB.Input.McpApprovalResponse] = deriveConfiguredEncoder
  implicit val rrMcpToolCallEncoder: Encoder[RRB.Input.McpToolCall] = deriveConfiguredEncoder
  implicit val rrItemReferenceEncoder: Encoder[RRB.Input.ItemReference] = deriveConfiguredEncoder
  // flat `"type"` discriminator matches the snake_case constructor names; the `message` branch delegates to `rrbInputMessageEncoder`
  // (OpenAIManualCodecs), which encodes input/output messages without an extra tag.
  // `InputMessage`/`OutputMessage` both serialize under the shared OpenAI `"type":"message"` discriminator (the configured derivation would
  // otherwise emit the snake_case constructor names `input_message` / `output_message`).
  implicit val rrbInputEncoder: Encoder[RRB.Input] = deriveConfiguredEncoder[RRB.Input].mapJson(_.mapObject { o =>
    o("type").flatMap(_.asString) match {
      case Some("input_message") | Some("output_message") => o.add("type", Json.fromString("message"))
      case _                                              => o
    }
  })
  // rrFormatEncoder: Encoder[RRB.Format] -- hand-written in OpenAIManualCodecs (responsesRequestFormatEncoder) so the `json_schema` case
  // can gate strict-mode normalization on the actual `strict` flag; see that file for rationale.
  implicit val rrPromptConfigEncoder: Encoder[RRB.PromptConfig] = deriveConfiguredEncoder
  implicit val rrReasoningConfigEncoder: Encoder[RRB.ReasoningConfig] = deriveConfiguredEncoder
  implicit val rrTextConfigEncoder: Encoder[RRB.TextConfig] = deriveConfiguredEncoder
  implicit val responsesRequestBodyEncoder: Encoder[RRB] = deriveConfiguredEncoder

  // responses ResponsesResponseBody (response, decode-only)
  implicit val rrespErrorObjectDecoder: Decoder[RRESP.ErrorObject] = deriveConfiguredDecoder
  implicit val rrespIncompleteDetailsDecoder: Decoder[RRESP.IncompleteDetails] = deriveConfiguredDecoder
  implicit val rrespPromptConfigDecoder: Decoder[RRESP.PromptConfig] = deriveConfiguredDecoder
  implicit val rrespReasoningConfigDecoder: Decoder[RRESP.ReasoningConfig] = deriveConfiguredDecoder
  implicit val rrespInFileSearchResultDecoder: Decoder[RRESP.InputItem.FileSearchResult] = deriveConfiguredDecoder
  implicit val rrespInPendingSafetyCheckDecoder: Decoder[RRESP.InputItem.ComputerCall.PendingSafetyCheck] = deriveConfiguredDecoder
  implicit val rrespInComputerScreenshotDecoder: Decoder[RRESP.InputItem.ComputerCallOutput.ComputerScreenshot] =
    deriveConfiguredDecoder
  implicit val rrespInAckSafetyCheckDecoder: Decoder[RRESP.InputItem.ComputerCallOutput.AcknowledgedSafetyCheck] =
    deriveConfiguredDecoder
  implicit val rrespInLocalShellActionDecoder: Decoder[RRESP.InputItem.LocalShellCall.Action] = deriveConfiguredDecoder
  implicit val rrespInMcpToolDecoder: Decoder[RRESP.InputItem.McpListTools.Tool] = deriveConfiguredDecoder
  implicit val rrespInSummaryTextDecoder: Decoder[RRESP.InputItem.Reasoning.SummaryText] = deriveConfiguredDecoder
  implicit val rrespInReasoningTextDecoder: Decoder[RRESP.InputItem.Reasoning.ReasoningText] = deriveConfiguredDecoder
  implicit val rrespOutPendingSafetyCheckDecoder: Decoder[RRESP.OutputItem.PendingSafetyCheck] = deriveConfiguredDecoder
  implicit val rrespOutSummaryTextDecoder: Decoder[RRESP.OutputItem.SummaryText] = deriveConfiguredDecoder
  implicit val rrespOutFileSearchResultDecoder: Decoder[RRESP.OutputItem.FileSearchResult] = deriveConfiguredDecoder
  implicit val rrespOutLocalShellActionDecoder: Decoder[RRESP.OutputItem.LocalShellAction] = deriveConfiguredDecoder
  implicit val rrespOutMcpToolDecoder: Decoder[RRESP.OutputItem.McpListTools.Tool] = deriveConfiguredDecoder
  implicit val rrespTopLogProbDecoder: Decoder[RRESP.OutputContent.TopLogProb] = deriveConfiguredDecoder
  implicit val rrespLogProbDecoder: Decoder[RRESP.OutputContent.LogProb] = deriveConfiguredDecoder
  implicit val rrespInputTokensDetailsDecoder: Decoder[RRESP.InputTokensDetails] = deriveConfiguredDecoder
  implicit val rrespOutputTokensDetailsDecoder: Decoder[RRESP.OutputTokensDetails] = deriveConfiguredDecoder
  implicit val rrespUsageDecoder: Decoder[RRESP.Usage] = deriveConfiguredDecoder
  implicit val rrespInInputContentDecoder: Decoder[RRESP.InputItem.InputContent] = deriveConfiguredDecoder
  implicit val rrespInOutputContentDecoder: Decoder[RRESP.InputItem.OutputContent] = deriveConfiguredDecoder
  implicit val rrespInComputerActionDecoder: Decoder[RRESP.InputItem.ComputerCall.Action] = deriveConfiguredDecoder
  implicit val rrespInWebActionDecoder: Decoder[RRESP.InputItem.WebSearchCall.Action] = deriveConfiguredDecoder
  implicit val rrespInCodeOutputDecoder: Decoder[RRESP.InputItem.CodeInterpreterCall.Output] = deriveConfiguredDecoder
  implicit val rrespOutComputerActionDecoder: Decoder[RRESP.OutputItem.ComputerCall.Action] = deriveConfiguredDecoder
  implicit val rrespOutWebActionDecoder: Decoder[RRESP.OutputItem.WebSearchCall.Action] = deriveConfiguredDecoder
  implicit val rrespOutCodeInterpreterOutputDecoder: Decoder[RRESP.OutputItem.CodeInterpreterOutput] = deriveConfiguredDecoder
  implicit val rrespAnnotationDecoder: Decoder[RRESP.OutputContent.Annotation] = deriveConfiguredDecoder
  implicit val rrespOutputContentDecoder: Decoder[RRESP.OutputContent] = deriveConfiguredDecoder
  implicit val rrespFormatDecoder: Decoder[RRESP.Format] = deriveConfiguredDecoder
  implicit val rrespInInputMessageDecoder: Decoder[RRESP.InputItem.InputMessage] = deriveConfiguredDecoder
  implicit val rrespInOutputMessageDecoder: Decoder[RRESP.InputItem.OutputMessage] = deriveConfiguredDecoder
  implicit val rrespInFileSearchCallDecoder: Decoder[RRESP.InputItem.FileSearchCall] = deriveConfiguredDecoder
  implicit val rrespInComputerCallDecoder: Decoder[RRESP.InputItem.ComputerCall] = deriveConfiguredDecoder
  implicit val rrespInComputerCallOutputDecoder: Decoder[RRESP.InputItem.ComputerCallOutput] = deriveConfiguredDecoder
  implicit val rrespInWebSearchCallDecoder: Decoder[RRESP.InputItem.WebSearchCall] = deriveConfiguredDecoder
  implicit val rrespInFunctionCallDecoder: Decoder[RRESP.InputItem.FunctionCall] = deriveConfiguredDecoder
  implicit val rrespInFunctionCallOutputDecoder: Decoder[RRESP.InputItem.FunctionCallOutput] = deriveConfiguredDecoder
  implicit val rrespInReasoningDecoder: Decoder[RRESP.InputItem.Reasoning] = deriveConfiguredDecoder
  implicit val rrespInImageGenerationCallDecoder: Decoder[RRESP.InputItem.ImageGenerationCall] = deriveConfiguredDecoder
  implicit val rrespInCodeInterpreterCallDecoder: Decoder[RRESP.InputItem.CodeInterpreterCall] = deriveConfiguredDecoder
  implicit val rrespInLocalShellCallDecoder: Decoder[RRESP.InputItem.LocalShellCall] = deriveConfiguredDecoder
  implicit val rrespInLocalShellCallOutputDecoder: Decoder[RRESP.InputItem.LocalShellCallOutput] = deriveConfiguredDecoder
  implicit val rrespInMcpListToolsDecoder: Decoder[RRESP.InputItem.McpListTools] = deriveConfiguredDecoder
  implicit val rrespInMcpApprovalRequestDecoder: Decoder[RRESP.InputItem.McpApprovalRequest] = deriveConfiguredDecoder
  implicit val rrespInMcpApprovalResponseDecoder: Decoder[RRESP.InputItem.McpApprovalResponse] = deriveConfiguredDecoder
  implicit val rrespInMcpToolCallDecoder: Decoder[RRESP.InputItem.McpToolCall] = deriveConfiguredDecoder
  implicit val rrespInCustomToolCallOutputDecoder: Decoder[RRESP.InputItem.CustomToolCallOutput] = deriveConfiguredDecoder
  implicit val rrespInCustomToolCallDecoder: Decoder[RRESP.InputItem.CustomToolCall] = deriveConfiguredDecoder
  implicit val rrespInItemReferenceDecoder: Decoder[RRESP.InputItem.ItemReference] = deriveConfiguredDecoder
  // flat `"type"` discriminator matches the snake_case constructor names; the `message` branch delegates to `rrespMessageDecoder`
  // (OpenAIManualCodecs), which distinguishes input/output messages structurally.
  implicit val rrespInputItemDecoder: Decoder[RRESP.InputItem] = deriveConfiguredDecoder
  implicit val rrespOutputItemDecoder: Decoder[RRESP.OutputItem] = deriveConfiguredDecoder
  implicit val rrespTextConfigDecoder: Decoder[RRESP.TextConfig] = deriveConfiguredDecoder
  implicit val responsesResponseBodyDecoder: Decoder[RRESP] = {
    import sttp.ai.core.json.CirceHelpers.emptyMapAsNone // local: empty `metadata` object -> None
    deriveConfiguredDecoder
  }
  implicit val deleteModelResponseResponseDecoder: Decoder[DeleteModelResponseResponse] = deriveConfiguredDecoder
}
