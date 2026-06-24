package sttp.ai.openai.json

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.derivation.{Configuration, ConfiguredCodec, ConfiguredDecoder, ConfiguredEncoder, ConfiguredEnumCodec}
import sttp.ai.core.json.CirceConfiguration.jsonConfiguration
import sttp.ai.core.json.CirceCodecs.dropEmptyTopLevel
import scala.deriving.Mirror
import sttp.ai.openai.requests.completions.{CompletionTokensDetails, PromptTokensDetails, Usage}
import sttp.ai.openai.requests.completions.chat.Audio
import sttp.ai.openai.requests.completions.chat.{ChatChunkRequestResponseData => Chunk, ChatRequestResponseData => Resp}
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.ChatBody
import sttp.ai.openai.requests.completions.chat.{ChatRequestBody, Role}
import sttp.ai.openai.requests.completions.chat.message.Attachment
import sttp.ai.openai.requests.completions.chat.message.Message.*
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
  Hyperparameters,
  Integration => FtIntegration,
  Method => FtMethod,
  Metrics,
  Supervised,
  Type => FtType,
  Wandb
}
import sttp.ai.openai.requests.finetuning.{
  Error => FtError,
  FineTuningJobCheckpointResponse,
  FineTuningJobEventResponse,
  FineTuningJobRequestBody,
  FineTuningJobResponse,
  ListFineTuningJobCheckpointResponse,
  ListFineTuningJobEventResponse,
  ListFineTuningJobResponse,
  Status
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
import OpenAIManualCodecs.*
import io.circe.syntax.*

/** Scala 3 configured (snake_case) codec registry for OpenAI case classes whose JSON keys differ from their Scala field names. Single-word
  * case classes use `deriveCodec` in-companion; sealed-trait dispatch / enums use hand-written codecs in-companion. Entries are ordered
  * leaf-first so nested implicits are initialized before the codecs that depend on them.
  */
object OpenAIDerivedCodecs {

  import io.circe.syntax.*

  /** Encoder for OpenAI-style polymorphism `{"type":"<constructor>", "<constructor>": {<fields>}}`. Derives the discriminator-less wrapper
    * form and lifts the constructor key into a flat `"type"` field; bare case objects collapse to `{"type":"<constructor>"}`.
    */
  inline def deriveAdtEncoder[A](using Mirror.SumOf[A]): Encoder[A] = {
    given Configuration = jsonConfiguration.copy(discriminator = None)
    ConfiguredEncoder.derived[A].mapJsonObject { obj =>
      obj.toList.headOption match {
        case Some((objType, objValue)) if objValue.asObject.exists(_.nonEmpty) => obj.+:("type" := objType)
        case Some((objType, _))                                                => JsonObject("type" := objType)
        case None                                                              => obj
      }
    }
  }

  /** Decoder counterpart of [[deriveAdtEncoder]]: first tries the discriminator-less wrapper form (matching the nested output, ignoring the
    * extra `"type"` field), then falls back to the flat `"type"`-discriminated form (bare case objects, flat subtypes).
    */
  inline def deriveAdtDecoder[A](using Mirror.SumOf[A]): Decoder[A] = {
    val withoutDiscriminator = {
      given Configuration = jsonConfiguration.copy(discriminator = None)
      ConfiguredDecoder.derived[A]
    }
    val withDiscriminator = ConfiguredDecoder.derived[A]
    withoutDiscriminator.or(withDiscriminator)
  }

  implicit val functionCallCodec: Codec[FunctionCall] = ConfiguredCodec.derived
  implicit val imageUrlDetailsCodec: Codec[MsgContent.ImageUrlDetails] = ConfiguredCodec.derived
  implicit val contentPartTextCodec: Codec[MsgContent.ContentPart.Text] = ConfiguredCodec.derived
  implicit val contentPartImageUrlCodec: Codec[MsgContent.ContentPart.ImageUrl] = ConfiguredCodec.derived
  implicit val msgContentPartCodec: Codec[MsgContent.ContentPart] = ConfiguredCodec.derived
  implicit val msgContentCodec: Codec[MsgContent] = Codec.from(
    Decoder[String]
      .map(MsgContent.TextContent(_): MsgContent)
      .or(Decoder[Seq[MsgContent.ContentPart]].map(MsgContent.ArrayContent(_): MsgContent)),
    Encoder.instance {
      case MsgContent.TextContent(value)  => value.asJson
      case MsgContent.ArrayContent(value) => value.asJson
    }
  )
  implicit val expiresAfterCodec: Codec[ExpiresAfter] = ConfiguredCodec.derived
  implicit val chatContentPartEncoder: Encoder[ChatRequestBody.ContentPart] = ConfiguredEncoder.derived
  implicit val chatPredictionEncoder: Encoder[ChatRequestBody.Prediction] = ConfiguredEncoder.derived
  implicit val chatStreamOptionsEncoder: Encoder[ChatRequestBody.StreamOptions] = ConfiguredEncoder.derived
  implicit val updateChatCompletionRequestBodyEncoder: Encoder[ChatRequestBody.UpdateChatCompletionRequestBody] = ConfiguredEncoder.derived

  implicit val chatResponseFormatEncoder: Encoder[ChatRequestBody.ResponseFormat] = deriveAdtEncoder[ChatRequestBody.ResponseFormat]
  implicit val msgCustomFormatCodec: Codec[MsgTool.Custom.Format] = ConfiguredCodec.derived
  implicit val msgToolEncoder: Encoder[MsgTool] = {
    import sttp.ai.core.json.CirceCodecs.omitFalse
    deriveAdtEncoder[MsgTool]
  }
  implicit val msgToolDecoder: Decoder[MsgTool] = deriveAdtDecoder[MsgTool]
  implicit val msgToolCodec: Codec[MsgTool] = Codec.from(msgToolDecoder, msgToolEncoder)

  implicit val assistantsToolEncoder: Encoder[AssistantsTool] = deriveAdtEncoder[AssistantsTool].mapJson(_.deepDropNullValues)
  implicit val assistantsToolDecoder: Decoder[AssistantsTool] = deriveAdtDecoder[AssistantsTool]
  implicit val assistantsToolCodec: Codec[AssistantsTool] = Codec.from(assistantsToolDecoder, assistantsToolEncoder)

  implicit val codeInterpreterToolResourceCodec: Codec[ToolResource.CodeInterpreterToolResource] = ConfiguredCodec.derived
  implicit val fileSearchToolResourceCodec: Codec[ToolResource.FileSearchToolResource] = ConfiguredCodec.derived
  implicit val toolResourcesCodec: Codec[ToolResources] = ConfiguredCodec.derived

  implicit val toolChoiceModeEncoder: Encoder[ToolChoice.AllowedTools.Mode] = ConfiguredEnumCodec.derived[ToolChoice.AllowedTools.Mode]
  implicit val toolChoiceEncoder: Encoder[ToolChoice] = {
    val objectCases = deriveAdtEncoder[ToolChoice]
    Encoder.instance {
      case ToolChoice.None     => Json.fromString("none")
      case ToolChoice.Auto     => Json.fromString("auto")
      case ToolChoice.Required => Json.fromString("required")
      case other               => objectCases(other)
    }
  }

  implicit val roleStandardCodec: Codec[Role.Standard] = ConfiguredEnumCodec.derived
  implicit val roleCodec: Codec[Role] = Codec.from(
    roleStandardCodec or Decoder.decodeString.map(Role.Custom(_)),
    Encoder.instance[Role] {
      case s: Role.Standard => roleStandardCodec(s)
      case Role.Custom(v)   => Json.fromString(v)
    }
  )

  implicit val assistantsReasoningEffortStandardCodec: Codec[AssistantsReasoningEffort.Standard] = ConfiguredEnumCodec.derived
  implicit val assistantsReasoningEffortEncoder: Encoder[AssistantsReasoningEffort] = Encoder.instance[AssistantsReasoningEffort] {
    case s: AssistantsReasoningEffort.Standard              => assistantsReasoningEffortStandardCodec(s)
    case AssistantsReasoningEffort.CustomReasoningEffort(v) => Json.fromString(v)
  }

  implicit val chatReasoningEffortStandardCodec: Codec[ChatRequestBody.ReasoningEffort.Standard] = ConfiguredEnumCodec.derived
  implicit val chatReasoningEffortEncoder: Encoder[ChatRequestBody.ReasoningEffort] = Encoder.instance[ChatRequestBody.ReasoningEffort] {
    case s: ChatRequestBody.ReasoningEffort.Standard              => chatReasoningEffortStandardCodec(s)
    case ChatRequestBody.ReasoningEffort.CustomReasoningEffort(v) => Json.fromString(v)
  }

  implicit val chatVoiceStandardCodec: Codec[ChatRequestBody.Voice.Standard] = ConfiguredEnumCodec.derived
  implicit val chatVoiceEncoder: Encoder[ChatRequestBody.Voice] = Encoder.instance[ChatRequestBody.Voice] {
    case s: ChatRequestBody.Voice.Standard    => chatVoiceStandardCodec(s)
    case ChatRequestBody.Voice.CustomVoice(v) => Json.fromString(v)
  }

  implicit val chatFormatStandardCodec: Codec[ChatRequestBody.Format.Standard] = ConfiguredEnumCodec.derived
  implicit val chatFormatEncoder: Encoder[ChatRequestBody.Format] = Encoder.instance[ChatRequestBody.Format] {
    case s: ChatRequestBody.Format.Standard     => chatFormatStandardCodec(s)
    case ChatRequestBody.Format.CustomFormat(v) => Json.fromString(v)
  }

  implicit val chatAudioEncoder: Encoder[ChatRequestBody.Audio] = ConfiguredEncoder.derived

  implicit val speechVoiceStandardCodec: Codec[SpeechVoice.Standard] = ConfiguredEnumCodec.derived
  implicit val speechVoiceEncoder: Encoder[SpeechVoice] = Encoder.instance[SpeechVoice] {
    case s: SpeechVoice.Standard    => speechVoiceStandardCodec(s)
    case SpeechVoice.CustomVoice(v) => Json.fromString(v)
  }

  implicit val speechResponseFormatStandardCodec: Codec[SpeechResponseFormat.Standard] = ConfiguredEnumCodec.derived
  implicit val speechResponseFormatEncoder: Encoder[SpeechResponseFormat] = Encoder.instance[SpeechResponseFormat] {
    case s: SpeechResponseFormat.Standard     => speechResponseFormatStandardCodec(s)
    case SpeechResponseFormat.CustomFormat(v) => Json.fromString(v)
  }

  implicit val imageResponseFormatStandardCodec: Codec[ImageResponseFormat.Standard] = ConfiguredEnumCodec.derived
  implicit val imageResponseFormatCodec: Codec[ImageResponseFormat] = Codec.from(
    imageResponseFormatStandardCodec or Decoder.decodeString.map(ImageResponseFormat.Custom(_)),
    Encoder.instance[ImageResponseFormat] {
      case s: ImageResponseFormat.Standard => imageResponseFormatStandardCodec(s)
      case ImageResponseFormat.Custom(v)   => Json.fromString(v)
    }
  )

  // chat request messages
  implicit val systemMessageCodec: Codec[System] = ConfiguredCodec.derived
  implicit val userMessageCodec: Codec[User] = ConfiguredCodec.derived
  implicit val assistantMessageCodec: Codec[Assistant] = ConfiguredCodec.derived
  implicit val toolMessageCodec: Codec[Tool] = ConfiguredCodec.derived
  implicit val messageCodec: Codec[Message] = {
    given jsonConfiguration: io.circe.derivation.Configuration =
      sttp.ai.core.json.CirceConfiguration.jsonConfiguration.withDiscriminator("role")
    Codec.from(ConfiguredDecoder.derived[Message], ConfiguredEncoder.derived[Message].mapJson(dropEmptyTopLevel))
  }

  implicit val chatBodyEncoder: Encoder[ChatBody] = ConfiguredEncoder.derived

  // usage
  implicit val completionTokensDetailsCodec: Codec[CompletionTokensDetails] = ConfiguredCodec.derived
  implicit val promptTokensDetailsCodec: Codec[PromptTokensDetails] = ConfiguredCodec.derived
  implicit val usageCodec: Codec[Usage] = ConfiguredCodec.derived

  // chat completion response
  implicit val audioCodec: Codec[Audio] = ConfiguredCodec.derived
  implicit val topLogprobsCodec: Codec[Resp.TopLogprobs] = ConfiguredCodec.derived
  implicit val logprobDataCodec: Codec[Resp.LogprobData] = ConfiguredCodec.derived
  implicit val logprobsCodec: Codec[Resp.Logprobs] = ConfiguredCodec.derived
  implicit val chatRespMessageCodec: Codec[Resp.Message] = ConfiguredCodec.derived
  implicit val chatRespChoicesCodec: Codec[Resp.Choices] = ConfiguredCodec.derived
  implicit val chatResponseCodec: Codec[Resp.ChatResponse] = ConfiguredCodec.derived
  implicit val listMessageResponseCodec: Codec[Resp.ListMessageResponse] = ConfiguredCodec.derived
  implicit val listChatResponseCodec: Codec[Resp.ListChatResponse] = ConfiguredCodec.derived
  implicit val deleteChatCompletionResponseCodec: Codec[Resp.DeleteChatCompletionResponse] = ConfiguredCodec.derived

  // chat chunk (streaming) response
  implicit val chunkDeltaCodec: Codec[Chunk.Delta] = ConfiguredCodec.derived
  implicit val chunkChoicesCodec: Codec[Chunk.Choices] = ConfiguredCodec.derived
  implicit val chatChunkResponseCodec: Codec[Chunk.ChatChunkResponse] = ConfiguredCodec.derived

  // request bodies
  implicit val embeddingsBodyEncoder: Encoder[EmbReq.EmbeddingsBody] = ConfiguredEncoder.derived
  implicit val moderationsBodyEncoder: Encoder[ModReq.ModerationsBody] = ConfiguredEncoder.derived
  implicit val completionsBodyEncoder: Encoder[CompReq.CompletionsBody] = ConfiguredEncoder.derived
  implicit val speechRequestBodyEncoder: Encoder[SpeechRequestBody] = ConfiguredEncoder.derived

  // embeddings response
  implicit val embeddingDataCodec: Codec[EmbResp.EmbeddingData] = ConfiguredCodec.derived
  implicit val embeddingsUsageCodec: Codec[EmbResp.Usage] = ConfiguredCodec.derived
  implicit val embeddingResponseCodec: Codec[EmbResp.EmbeddingResponse] = ConfiguredCodec.derived

  // moderations response
  implicit val categoryScoresCodec: Codec[ModResp.CategoryScores] = ConfiguredCodec.derived
  implicit val categoriesCodec: Codec[ModResp.Categories] = ConfiguredCodec.derived
  implicit val moderationResultCodec: Codec[ModResp.Result] = ConfiguredCodec.derived
  implicit val moderationDataCodec: Codec[ModResp.ModerationData] = ConfiguredCodec.derived

  // completions response
  implicit val completionsChoicesCodec: Codec[CompResp.Choices] = ConfiguredCodec.derived
  implicit val completionsResponseCodec: Codec[CompResp.CompletionsResponse] = ConfiguredCodec.derived

  // models response
  implicit val deletedModelDataCodec: Codec[ModelsResponseData.DeletedModelData] = ConfiguredCodec.derived
  implicit val modelDataCodec: Codec[ModelsResponseData.ModelData] = ConfiguredCodec.derived
  implicit val modelsResponseCodec: Codec[ModelsResponseData.ModelsResponse] = ConfiguredCodec.derived

  // files response
  implicit val fileDataCodec: Codec[FilesResponseData.FileData] = ConfiguredCodec.derived
  implicit val filesResponseCodec: Codec[FilesResponseData.FilesResponse] = ConfiguredCodec.derived
  implicit val deletedFileDataCodec: Codec[FilesResponseData.DeletedFileData] = ConfiguredCodec.derived

  // audio response
  implicit val audioResponseCodec: Codec[AudioResponseData.AudioResponse] = ConfiguredCodec.derived

  // images
  implicit val generatedImageDataCodec: Codec[ImageResponseData.GeneratedImageData] = ConfiguredCodec.derived
  implicit val imageResponseCodec: Codec[ImageResponseData.ImageResponse] = ConfiguredCodec.derived
  implicit val imageCreationBodyEncoder: Encoder[ImageCreationRequestBody.ImageCreationBody] = ConfiguredEncoder.derived

  // assistants
  implicit val createAssistantBodyEncoder: Encoder[AssistantsRequestBody.CreateAssistantBody] =
    ConfiguredEncoder.derived[AssistantsRequestBody.CreateAssistantBody].mapJson(dropEmptyTopLevel)
  implicit val modifyAssistantBodyEncoder: Encoder[AssistantsRequestBody.ModifyAssistantBody] =
    ConfiguredEncoder.derived[AssistantsRequestBody.ModifyAssistantBody].mapJson(dropEmptyTopLevel)
  implicit val assistantDataCodec: Codec[AssistantsResponseData.AssistantData] = ConfiguredCodec.derived
  implicit val listAssistantsResponseCodec: Codec[AssistantsResponseData.ListAssistantsResponse] = ConfiguredCodec.derived
  implicit val deleteAssistantResponseCodec: Codec[AssistantsResponseData.DeleteAssistantResponse] = ConfiguredCodec.derived

  // finetuning
  implicit val finetuningTypeCodec: Codec[FtType] =
    OpenAIManualCodecs.typeCodec(Map("wandb" -> FtIntegration.Wandb, "supervised" -> FtMethod.Supervised, "dpo" -> FtMethod.Dpo))
  implicit val hyperparametersCodec: Codec[Hyperparameters] = ConfiguredCodec.derived
  implicit val wandbCodec: Codec[Wandb] = ConfiguredCodec.derived
  implicit val ftIntegrationCodec: Codec[FtIntegration] = ConfiguredCodec.derived
  implicit val supervisedCodec: Codec[Supervised] = ConfiguredCodec.derived
  implicit val dpoCodec: Codec[Dpo] = ConfiguredCodec.derived
  implicit val ftMethodCodec: Codec[FtMethod] = ConfiguredCodec.derived
  implicit val ftErrorCodec: Codec[FtError] = ConfiguredCodec.derived
  implicit val metricsCodec: Codec[Metrics] = ConfiguredCodec.derived
  implicit val standardStatusCodec: Codec[Status.Standard] = ConfiguredEnumCodec.derived
  implicit val fineTuningStatusCodec: Codec[Status] = Codec.from(
    standardStatusCodec or Decoder.decodeString.map(Status.Custom(_)),
    Encoder.instance[Status] {
      case s: Status.Standard    => standardStatusCodec(s)
      case Status.Custom(custom) => Json.fromString(custom)
    }
  )
  implicit val fineTuningJobResponseCodec: Codec[FineTuningJobResponse] = ConfiguredCodec.derived
  implicit val listFineTuningJobResponseCodec: Codec[ListFineTuningJobResponse] = ConfiguredCodec.derived
  implicit val fineTuningJobEventResponseDecoder: Decoder[FineTuningJobEventResponse] = ConfiguredDecoder.derived
  implicit val listFineTuningJobEventResponseCodec: Decoder[ListFineTuningJobEventResponse] = ConfiguredDecoder.derived
  implicit val fineTuningJobCheckpointResponseCodec: Codec[FineTuningJobCheckpointResponse] = ConfiguredCodec.derived
  implicit val listFineTuningJobCheckpointResponseCodec: Codec[ListFineTuningJobCheckpointResponse] = ConfiguredCodec.derived
  implicit val fineTuningJobRequestBodyEncoder: Encoder[FineTuningJobRequestBody] = ConfiguredEncoder.derived

  // batch
  implicit val batchRequestBodyEncoder: Encoder[BatchRequestBody] = ConfiguredEncoder.derived
  implicit val batchDataCodec: Codec[BatchData] = ConfiguredCodec.derived
  implicit val errorsCodec: Codec[Errors] = ConfiguredCodec.derived
  implicit val requestCountsCodec: Codec[RequestCounts] = ConfiguredCodec.derived
  implicit val batchResponseCodec: Codec[BatchResponse] = ConfiguredCodec.derived
  implicit val listBatchResponseCodec: Codec[ListBatchResponse] = ConfiguredCodec.derived

  // upload
  implicit val uploadRequestBodyEncoder: Encoder[UploadRequestBody] = ConfiguredEncoder.derived
  implicit val completeUploadRequestBodyEncoder: Encoder[CompleteUploadRequestBody] = ConfiguredEncoder.derived
  implicit val fileMetadataCodec: Codec[FileMetadata] = ConfiguredCodec.derived
  implicit val uploadResponseCodec: Codec[UploadResponse] = ConfiguredCodec.derived
  implicit val uploadPartResponseCodec: Codec[UploadPartResponse] = ConfiguredCodec.derived

  // admin
  implicit val adminApiKeyRequestBodyEncoder: Encoder[AdminApiKeyRequestBody] = ConfiguredEncoder.derived
  implicit val ownerCodec: Codec[Owner] = ConfiguredCodec.derived
  implicit val adminApiKeyResponseCodec: Codec[AdminApiKeyResponse] = ConfiguredCodec.derived
  implicit val listAdminApiKeyResponseCodec: Codec[ListAdminApiKeyResponse] = ConfiguredCodec.derived
  implicit val deleteAdminApiKeyResponseCodec: Codec[DeleteAdminApiKeyResponse] = ConfiguredCodec.derived

  // vectorstore (enumeration codecs: case-object enums encoded as snake_case strings)
  implicit val fileStatusCodec: Codec[FileStatus] = ConfiguredEnumCodec.derived
  implicit val storeStatusCodec: Codec[VectorStoreResponseData.StoreStatus] = ConfiguredEnumCodec.derived
  implicit val errorCodeCodec: Codec[VectorStoreFileResponseData.ErrorCode] = ConfiguredEnumCodec.derived

  implicit val createVectorStoreBodyEncoder: Encoder[VectorStoreRequestBody.CreateVectorStoreBody] = ConfiguredEncoder.derived
  implicit val modifyVectorStoreBodyEncoder: Encoder[VectorStoreRequestBody.ModifyVectorStoreBody] = ConfiguredEncoder.derived
  implicit val fileCountsCodec: Codec[VectorStoreResponseData.FileCounts] = ConfiguredCodec.derived
  implicit val vectorStoreCodec: Codec[VectorStoreResponseData.VectorStore] = ConfiguredCodec.derived
  implicit val listVectorStoresResponseCodec: Codec[VectorStoreResponseData.ListVectorStoresResponse] = ConfiguredCodec.derived
  implicit val deleteVectorStoreResponseCodec: Codec[VectorStoreResponseData.DeleteVectorStoreResponse] = ConfiguredCodec.derived
  implicit val createVectorStoreFileBodyEncoder: Encoder[VectorStoreFileRequestBody.CreateVectorStoreFileBody] = ConfiguredEncoder.derived
  implicit val listVectorStoreFilesBodyEncoder: Encoder[VectorStoreFileRequestBody.ListVectorStoreFilesBody] = ConfiguredEncoder.derived
  implicit val lastErrorCodec: Codec[VectorStoreFileResponseData.LastError] = ConfiguredCodec.derived
  implicit val vectorStoreFileCodec: Codec[VectorStoreFileResponseData.VectorStoreFile] = ConfiguredCodec.derived
  implicit val listVectorStoreFilesResponseCodec: Codec[VectorStoreFileResponseData.ListVectorStoreFilesResponse] = ConfiguredCodec.derived
  implicit val deleteVectorStoreFileResponseCodec: Codec[VectorStoreFileResponseData.DeleteVectorStoreFileResponse] =
    ConfiguredCodec.derived

  // threads request bodies
  implicit val attachmentCodec: Codec[Attachment] = {
    import sttp.ai.core.json.CirceCodecs.emptyIterableAsNone // local: empty `tools` array -> None
    ConfiguredCodec.derived
  }
  implicit val createMessageEncoder: Encoder[ThreadMessagesRequestBody.CreateMessage] = ConfiguredEncoder.derived
  implicit val createThreadBodyEncoder: Encoder[ThreadsRequestBody.CreateThreadBody] = ConfiguredEncoder.derived
  implicit val toolOutputEncoder: Encoder[ThreadRunsRequestBody.ToolOutput] = ConfiguredEncoder.derived
  implicit val submitToolOutputsToRunEncoder: Encoder[ThreadRunsRequestBody.SubmitToolOutputsToRun] = ConfiguredEncoder.derived
  implicit val modifyRunEncoder: Encoder[ThreadRunsRequestBody.ModifyRun] =
    ConfiguredEncoder.derived[ThreadRunsRequestBody.ModifyRun].mapJson(dropEmptyTopLevel)
  implicit val createRunEncoder: Encoder[ThreadRunsRequestBody.CreateRun] =
    ConfiguredEncoder.derived[ThreadRunsRequestBody.CreateRun].mapJson(dropEmptyTopLevel)
  implicit val createThreadAndRunEncoder: Encoder[ThreadRunsRequestBody.CreateThreadAndRun] =
    ConfiguredEncoder.derived[ThreadRunsRequestBody.CreateThreadAndRun].mapJson(dropEmptyTopLevel)
  // threads response
  implicit val threadDataCodec: Codec[ThreadsResponseData.ThreadData] = ConfiguredCodec.derived
  implicit val deleteThreadResponseCodec: Codec[ThreadsResponseData.DeleteThreadResponse] = ConfiguredCodec.derived

  // thread messages response (decode-only)
  implicit val fileCitationDetailsDecoder: Decoder[TMR.Annotation.FileCitationDetails] = ConfiguredDecoder.derived
  implicit val filePathDetailsDecoder: Decoder[TMR.Annotation.FilePathDetails] = ConfiguredDecoder.derived
  implicit val fileCitationDecoder: Decoder[TMR.Annotation.FileCitation] = ConfiguredDecoder.derived
  implicit val filePathDecoder: Decoder[TMR.Annotation.FilePath] = ConfiguredDecoder.derived
  implicit val tmrAnnotationDecoder: Decoder[TMR.Annotation] = ConfiguredDecoder.derived
  implicit val textContentValueDecoder: Decoder[TMR.Content.TextContentValue] = ConfiguredDecoder.derived
  implicit val textDecoder: Decoder[TMR.Content.Text] = ConfiguredDecoder.derived
  implicit val imageFileDetailsDecoder: Decoder[TMR.Content.ImageFileDetails] = ConfiguredDecoder.derived
  implicit val imageFileDecoder: Decoder[TMR.Content.ImageFile] = ConfiguredDecoder.derived
  implicit val tmrContentDecoder: Decoder[TMR.Content] = ConfiguredDecoder.derived
  implicit val messageDataDecoder: Decoder[TMR.MessageData] =
    ConfiguredDecoder.derived[TMR.MessageData].prepare(_.withFocus(dropEmptyTopLevel))
  implicit val listMessagesResponseDecoder: Decoder[TMR.ListMessagesResponse] = ConfiguredDecoder.derived
  implicit val deleteMessageResponseDecoder: Decoder[TMR.DeleteMessageResponse] = ConfiguredDecoder.derived

  // thread runs response (decode-only)
  implicit val runsUsageDecoder: Decoder[TRR.Usage] = ConfiguredDecoder.derived
  implicit val functionCallResultDecoder: Decoder[TRR.ToolCall.FunctionCallResult] = ConfiguredDecoder.derived
  implicit val fileSearchResultContentDecoder: Decoder[TRR.ToolCall.FileSearch.FileSearchDetails.FileSearchResult.Content] =
    ConfiguredDecoder.derived
  implicit val fileSearchResultDecoder: Decoder[TRR.ToolCall.FileSearch.FileSearchDetails.FileSearchResult] = ConfiguredDecoder.derived
  implicit val rankingOptionsDecoder: Decoder[TRR.ToolCall.FileSearch.FileSearchDetails.RankingOptions] = ConfiguredDecoder.derived
  implicit val fileSearchInnerDecoder: Decoder[TRR.ToolCall.FileSearch.FileSearchDetails] = ConfiguredDecoder.derived
  implicit val codeInterpreterDecoder: Decoder[TRR.ToolCall.CodeInterpreter] = ConfiguredDecoder.derived
  implicit val fileSearchDecoder: Decoder[TRR.ToolCall.FileSearch] = ConfiguredDecoder.derived
  implicit val functionDecoder: Decoder[TRR.ToolCall.Function] = ConfiguredDecoder.derived
  implicit val trrToolCallDecoder: Decoder[TRR.ToolCall] = ConfiguredDecoder.derived
  implicit val toolCallsDecoder: Decoder[TRR.ToolCalls] = ConfiguredDecoder.derived
  implicit val messageCreationDecoder: Decoder[TRR.MessageCreation] = ConfiguredDecoder.derived
  // dispatch on `"type"`: `tool_calls` decodes the flat object, while `message_creation` pulls its payload from the nested
  // `message_creation` field (so the two branches have different shapes and this can't be a uniform configured ADT).
  implicit val trrStepDetailsDecoder: Decoder[TRR.StepDetails] = Decoder.instance { c =>
    c.get[String]("type").flatMap {
      case "message_creation" => messageCreationDecoder.at("message_creation")(c)
      case "tool_calls"       => c.as[TRR.ToolCalls]
      case other              => Left(DecodingFailure(s"Unknown step details type: $other", c.history))
    }
  }
  implicit val submitToolOutputsDecoder: Decoder[TRR.SubmitToolOutputs] = ConfiguredDecoder.derived
  implicit val submitToolOutputsRequiredActionDecoder: Decoder[TRR.RequiredAction.SubmitToolOutputs] = ConfiguredDecoder.derived
  // configured ADT on the default `"type"` discriminator: the single subtype's snake_case name (`submit_tool_outputs`) matches the
  // discriminator value. Replaces the hand-written dispatch that previously lived in OpenAIManualCodecs.
  implicit val trrRequiredActionDecoder: Decoder[TRR.RequiredAction] = ConfiguredDecoder.derived
  implicit val serverErrorDecoder: Decoder[TRR.Error.ServerError] = ConfiguredDecoder.derived
  implicit val rateLimitExceededDecoder: Decoder[TRR.Error.RateLimitExceeded] = ConfiguredDecoder.derived
  implicit val invalidPromptDecoder: Decoder[TRR.Error.InvalidPrompt] = ConfiguredDecoder.derived
  implicit val trrErrorDecoder: Decoder[TRR.Error] = {
    given jsonConfiguration: io.circe.derivation.Configuration =
      sttp.ai.core.json.CirceConfiguration.jsonConfiguration.withDiscriminator("code")
    ConfiguredDecoder.derived[TRR.Error]
  }
  implicit val runDataDecoder: Decoder[TRR.RunData] = ConfiguredDecoder.derived
  implicit val listRunsResponseDecoder: Decoder[TRR.ListRunsResponse] = ConfiguredDecoder.derived
  implicit val runStepDataDecoder: Decoder[TRR.RunStepData] = ConfiguredDecoder.derived
  implicit val listRunStepsResponseDecoder: Decoder[TRR.ListRunStepsResponse] = ConfiguredDecoder.derived

  // responses Tool (request, encode-only) leaf encoders
  implicit val respUserLocationEncoder: Codec[RespTool.UserLocation] = ConfiguredCodec.derived
  implicit val respFunctionEncoder: Codec[RespTool.Function] = ConfiguredCodec.derived
  implicit val respFsMetadataEncoder: Codec[RespTool.FileSearch.Filter.Metadata] = ConfiguredCodec.derived
  implicit val respFsFileIdsEncoder: Codec[RespTool.FileSearch.Filter.FileIds] = ConfiguredCodec.derived
  implicit val respFsFilterCodec: Codec[RespTool.FileSearch.Filter] = ConfiguredCodec.derived
  implicit val respRankingOptionsEncoder: Codec[RespTool.FileSearch.RankingOptions] = ConfiguredCodec.derived
  implicit val respFileSearchEncoder: Codec[RespTool.FileSearch] = ConfiguredCodec.derived
  implicit val respDefaultWebSearchPreviewEncoder: Codec[RespTool.WebSearchPreview.DefaultWebSearchPreview] = ConfiguredCodec.derived
  implicit val respWebSearchPreview20250311Encoder: Codec[RespTool.WebSearchPreview.WebSearchPreview20250311] = ConfiguredCodec.derived
  implicit val respComputerUsePreviewEncoder: Codec[RespTool.ComputerUsePreview] = ConfiguredCodec.derived
  implicit val respApprovalAlwaysEncoder: Codec[RespTool.Mcp.ApprovalFilter.Always] = ConfiguredCodec.derived
  implicit val respApprovalNeverEncoder: Codec[RespTool.Mcp.ApprovalFilter.Never] = ConfiguredCodec.derived
  implicit val respRequireApprovalFilterEncoder: Codec[RespTool.Mcp.RequireApproval.Filter] = ConfiguredCodec.derived
  implicit val respMcpEncoder: Codec[RespTool.Mcp] = ConfiguredCodec.derived
  implicit val respContainerAutoEncoder: Codec[RespTool.CodeInterpreter.Container.ContainerAuto] = ConfiguredCodec.derived
  implicit val respCodeInterpreterEncoder: Codec[RespTool.CodeInterpreter] = ConfiguredCodec.derived
  implicit val respInputImageMaskEncoder: Codec[RespTool.ImageGeneration.InputImageMask] = ConfiguredCodec.derived
  implicit val respImageGenerationEncoder: Codec[RespTool.ImageGeneration] = ConfiguredCodec.derived
  implicit val respGrammarEncoder: Codec[RespTool.Custom.Format.Grammar] = ConfiguredCodec.derived
  implicit val respCustomFormatCodec: Codec[RespTool.Custom.Format] = ConfiguredCodec.derived
  implicit val respCustomEncoder: Codec[RespTool.Custom] = ConfiguredCodec.derived
  implicit val respLocalShellEncoder: Codec[RespTool.LocalShell] = ConfiguredCodec.derived

  // responses ToolChoice (request, encode-only) leaf encoders
  // enumeration codec: the case-object names snake_case to the JSON strings (none / auto / required)
  implicit val respToolChoiceModeCodec: Codec[RespTC.ToolChoiceMode] = ConfiguredEnumCodec.derived[RespTC.ToolChoiceMode]
  implicit val tcToolDefFunctionEncoder: Codec[RespTC.ToolChoiceObject.AllowedTools.ToolDefinition.Function] = ConfiguredCodec.derived
  implicit val tcToolDefMcpEncoder: Codec[RespTC.ToolChoiceObject.AllowedTools.ToolDefinition.Mcp] = ConfiguredCodec.derived
  implicit val respToolDefinitionCodec: Codec[RespTC.ToolChoiceObject.AllowedTools.ToolDefinition] = ConfiguredCodec.derived
  implicit val tcAllowedToolsEncoder: Codec[RespTC.ToolChoiceObject.AllowedTools] = ConfiguredCodec.derived
  implicit val tcFunctionEncoder: Codec[RespTC.ToolChoiceObject.Function] = ConfiguredCodec.derived
  implicit val tcMcpEncoder: Codec[RespTC.ToolChoiceObject.Mcp] = ConfiguredCodec.derived
  implicit val tcCustomEncoder: Codec[RespTC.ToolChoiceObject.Custom] = ConfiguredCodec.derived
  implicit val respToolChoiceObjectCodec: Codec[RespTC.ToolChoiceObject] = ConfiguredCodec.derived

  // responses InputItemsListResponseBody (response, decode-only)
  implicit val iiTopLogProbDecoder: Decoder[IIL.InputItem.OutputContent.TopLogProb] = ConfiguredDecoder.derived
  implicit val iiLogProbDecoder: Decoder[IIL.InputItem.OutputContent.LogProb] = ConfiguredDecoder.derived
  implicit val iiFileSearchResultDecoder: Decoder[IIL.InputItem.FileSearchResult] = ConfiguredDecoder.derived
  implicit val iiPendingSafetyCheckDecoder: Decoder[IIL.InputItem.ComputerCall.PendingSafetyCheck] = ConfiguredDecoder.derived
  implicit val iiComputerScreenshotDecoder: Decoder[IIL.InputItem.ComputerCallOutput.ComputerScreenshot] = ConfiguredDecoder.derived
  implicit val iiAcknowledgedSafetyCheckDecoder: Decoder[IIL.InputItem.ComputerCallOutput.AcknowledgedSafetyCheck] =
    ConfiguredDecoder.derived
  implicit val iiLocalShellActionDecoder: Decoder[IIL.InputItem.LocalShellCall.Action] = ConfiguredDecoder.derived
  implicit val iiMcpListToolsToolDecoder: Decoder[IIL.InputItem.McpListTools.Tool] = ConfiguredDecoder.derived
  implicit val iiInputContentDecoder: Decoder[IIL.InputItem.InputContent] = ConfiguredDecoder.derived
  implicit val iiAnnotationDecoder: Decoder[IIL.InputItem.OutputContent.Annotation] = ConfiguredDecoder.derived
  implicit val iiOutputContentDecoder: Decoder[IIL.InputItem.OutputContent] = ConfiguredDecoder.derived
  implicit val iiComputerActionDecoder: Decoder[IIL.InputItem.ComputerCall.Action] = ConfiguredDecoder.derived
  implicit val iiWebActionDecoder: Decoder[IIL.InputItem.WebSearchCall.Action] = ConfiguredDecoder.derived
  implicit val iiCodeOutputDecoder: Decoder[IIL.InputItem.CodeInterpreterCall.Output] = ConfiguredDecoder.derived
  implicit val iiInputMessageDecoder: Decoder[IIL.InputItem.InputMessage] = ConfiguredDecoder.derived
  implicit val iiOutputMessageDecoder: Decoder[IIL.InputItem.OutputMessage] = ConfiguredDecoder.derived
  implicit val iiFileSearchCallDecoder: Decoder[IIL.InputItem.FileSearchCall] = ConfiguredDecoder.derived
  implicit val iiComputerCallDecoder: Decoder[IIL.InputItem.ComputerCall] = ConfiguredDecoder.derived
  implicit val iiComputerCallOutputDecoder: Decoder[IIL.InputItem.ComputerCallOutput] = ConfiguredDecoder.derived
  implicit val iiWebSearchCallDecoder: Decoder[IIL.InputItem.WebSearchCall] = ConfiguredDecoder.derived
  implicit val iiFunctionCallDecoder: Decoder[IIL.InputItem.FunctionCall] = ConfiguredDecoder.derived
  implicit val iiFunctionCallOutputDecoder: Decoder[IIL.InputItem.FunctionCallOutput] = ConfiguredDecoder.derived
  implicit val iiImageGenerationCallDecoder: Decoder[IIL.InputItem.ImageGenerationCall] = ConfiguredDecoder.derived
  implicit val iiCodeInterpreterCallDecoder: Decoder[IIL.InputItem.CodeInterpreterCall] = ConfiguredDecoder.derived
  implicit val iiLocalShellCallDecoder: Decoder[IIL.InputItem.LocalShellCall] = ConfiguredDecoder.derived
  implicit val iiLocalShellCallOutputDecoder: Decoder[IIL.InputItem.LocalShellCallOutput] = ConfiguredDecoder.derived
  implicit val iiMcpListToolsDecoder: Decoder[IIL.InputItem.McpListTools] = ConfiguredDecoder.derived
  implicit val iiMcpApprovalRequestDecoder: Decoder[IIL.InputItem.McpApprovalRequest] = ConfiguredDecoder.derived
  implicit val iiMcpApprovalResponseDecoder: Decoder[IIL.InputItem.McpApprovalResponse] = ConfiguredDecoder.derived
  implicit val iiMcpToolCallDecoder: Decoder[IIL.InputItem.McpToolCall] = ConfiguredDecoder.derived
  implicit val iilInputItemDecoder: Decoder[IIL.InputItem] = ConfiguredDecoder.derived
  implicit val inputItemsListResponseBodyDecoder: Decoder[IIL] = ConfiguredDecoder.derived

  // responses ResponsesRequestBody (request, encode-only)
  implicit val rrTopLogProbEncoder: Encoder[RRB.Input.OutputContentItem.OutputText.TopLogProb] = ConfiguredEncoder.derived
  implicit val rrLogProbEncoder: Encoder[RRB.Input.OutputContentItem.OutputText.LogProb] = ConfiguredEncoder.derived
  implicit val rrFileSearchResultEncoder: Encoder[RRB.Input.FileSearchCall.FileSearchResult] = ConfiguredEncoder.derived
  implicit val rrPendingSafetyCheckEncoder: Encoder[RRB.Input.ComputerCall.PendingSafetyCheck] = ConfiguredEncoder.derived
  implicit val rrComputerScreenshotEncoder: Encoder[RRB.Input.ComputerCallOutput.ComputerScreenshot] = ConfiguredEncoder.derived
  implicit val rrAcknowledgedSafetyCheckEncoder: Encoder[RRB.Input.ComputerCallOutput.AcknowledgedSafetyCheck] =
    ConfiguredEncoder.derived
  implicit val rrSummaryTextEncoder: Encoder[RRB.Input.Reasoning.SummaryText] = ConfiguredEncoder.derived
  implicit val rrLocalShellActionEncoder: Encoder[RRB.Input.LocalShellCall.Action] = ConfiguredEncoder.derived
  implicit val rrMcpListToolsToolEncoder: Encoder[RRB.Input.McpListTools.Tool] = ConfiguredEncoder.derived
  implicit val rrInputContentItemEncoder: Encoder[RRB.Input.InputContentItem] = ConfiguredEncoder.derived
  implicit val rrAnnotationEncoder: Encoder[RRB.Input.OutputContentItem.OutputText.Annotation] = ConfiguredEncoder.derived
  implicit val rrOutputContentItemEncoder: Encoder[RRB.Input.OutputContentItem] = ConfiguredEncoder.derived
  implicit val rrComputerActionEncoder: Encoder[RRB.Input.ComputerCall.Action] = ConfiguredEncoder.derived
  implicit val rrWebActionEncoder: Encoder[RRB.Input.WebSearchCall.Action] = ConfiguredEncoder.derived
  implicit val rrCodeOutputEncoder: Encoder[RRB.Input.CodeInterpreterCall.Output] = ConfiguredEncoder.derived
  implicit val rrInputMessageEncoder: Encoder[RRB.Input.InputMessage] = ConfiguredEncoder.derived
  implicit val rrOutputMessageEncoder: Encoder[RRB.Input.OutputMessage] = ConfiguredEncoder.derived
  implicit val rrFileSearchCallEncoder: Encoder[RRB.Input.FileSearchCall] = ConfiguredEncoder.derived
  implicit val rrComputerCallEncoder: Encoder[RRB.Input.ComputerCall] = ConfiguredEncoder.derived
  implicit val rrComputerCallOutputEncoder: Encoder[RRB.Input.ComputerCallOutput] = ConfiguredEncoder.derived
  implicit val rrWebSearchCallEncoder: Encoder[RRB.Input.WebSearchCall] = ConfiguredEncoder.derived
  implicit val rrFunctionCallEncoder: Encoder[RRB.Input.FunctionCall] = ConfiguredEncoder.derived
  implicit val rrFunctionCallOutputEncoder: Encoder[RRB.Input.FunctionCallOutput] = ConfiguredEncoder.derived
  implicit val rrReasoningEncoder: Encoder[RRB.Input.Reasoning] = ConfiguredEncoder.derived
  implicit val rrImageGenerationCallEncoder: Encoder[RRB.Input.ImageGenerationCall] = ConfiguredEncoder.derived
  implicit val rrCodeInterpreterCallEncoder: Encoder[RRB.Input.CodeInterpreterCall] = ConfiguredEncoder.derived
  implicit val rrLocalShellCallEncoder: Encoder[RRB.Input.LocalShellCall] = ConfiguredEncoder.derived
  implicit val rrLocalShellCallOutputEncoder: Encoder[RRB.Input.LocalShellCallOutput] = ConfiguredEncoder.derived
  implicit val rrMcpListToolsEncoder: Encoder[RRB.Input.McpListTools] = ConfiguredEncoder.derived
  implicit val rrMcpApprovalRequestEncoder: Encoder[RRB.Input.McpApprovalRequest] = ConfiguredEncoder.derived
  implicit val rrMcpApprovalResponseEncoder: Encoder[RRB.Input.McpApprovalResponse] = ConfiguredEncoder.derived
  implicit val rrMcpToolCallEncoder: Encoder[RRB.Input.McpToolCall] = ConfiguredEncoder.derived
  implicit val rrItemReferenceEncoder: Encoder[RRB.Input.ItemReference] = ConfiguredEncoder.derived
  // `InputMessage`/`OutputMessage` both serialize under the shared OpenAI `"type":"message"` discriminator (the configured derivation would
  // otherwise emit the snake_case constructor names `input_message` / `output_message`).
  implicit val rrbInputEncoder: Encoder[RRB.Input] = ConfiguredEncoder
    .derived[RRB.Input]
    .mapJson(_.mapObject { o =>
      o("type").flatMap(_.asString) match {
        case Some("input_message") | Some("output_message") => o.add("type", Json.fromString("message"))
        case _                                              => o
      }
    })
  implicit val rrFormatEncoder: Encoder[RRB.Format] = ConfiguredEncoder.derived
  implicit val rrPromptConfigEncoder: Encoder[RRB.PromptConfig] = ConfiguredEncoder.derived
  implicit val rrReasoningConfigEncoder: Encoder[RRB.ReasoningConfig] = ConfiguredEncoder.derived
  implicit val rrTextConfigEncoder: Encoder[RRB.TextConfig] = ConfiguredEncoder.derived
  implicit val responsesRequestBodyEncoder: Encoder[RRB] = ConfiguredEncoder.derived

  // responses ResponsesResponseBody (response, decode-only)
  implicit val rrespErrorObjectDecoder: Decoder[RRESP.ErrorObject] = ConfiguredDecoder.derived
  implicit val rrespIncompleteDetailsDecoder: Decoder[RRESP.IncompleteDetails] = ConfiguredDecoder.derived
  implicit val rrespPromptConfigDecoder: Decoder[RRESP.PromptConfig] = ConfiguredDecoder.derived
  implicit val rrespReasoningConfigDecoder: Decoder[RRESP.ReasoningConfig] = ConfiguredDecoder.derived
  implicit val rrespInFileSearchResultDecoder: Decoder[RRESP.InputItem.FileSearchResult] = ConfiguredDecoder.derived
  implicit val rrespInPendingSafetyCheckDecoder: Decoder[RRESP.InputItem.ComputerCall.PendingSafetyCheck] = ConfiguredDecoder.derived
  implicit val rrespInComputerScreenshotDecoder: Decoder[RRESP.InputItem.ComputerCallOutput.ComputerScreenshot] =
    ConfiguredDecoder.derived
  implicit val rrespInAckSafetyCheckDecoder: Decoder[RRESP.InputItem.ComputerCallOutput.AcknowledgedSafetyCheck] =
    ConfiguredDecoder.derived
  implicit val rrespInLocalShellActionDecoder: Decoder[RRESP.InputItem.LocalShellCall.Action] = ConfiguredDecoder.derived
  implicit val rrespInMcpToolDecoder: Decoder[RRESP.InputItem.McpListTools.Tool] = ConfiguredDecoder.derived
  implicit val rrespInSummaryTextDecoder: Decoder[RRESP.InputItem.Reasoning.SummaryText] = ConfiguredDecoder.derived
  implicit val rrespInReasoningTextDecoder: Decoder[RRESP.InputItem.Reasoning.ReasoningText] = ConfiguredDecoder.derived
  implicit val rrespOutPendingSafetyCheckDecoder: Decoder[RRESP.OutputItem.PendingSafetyCheck] = ConfiguredDecoder.derived
  implicit val rrespOutSummaryTextDecoder: Decoder[RRESP.OutputItem.SummaryText] = ConfiguredDecoder.derived
  implicit val rrespOutFileSearchResultDecoder: Decoder[RRESP.OutputItem.FileSearchResult] = ConfiguredDecoder.derived
  implicit val rrespOutLocalShellActionDecoder: Decoder[RRESP.OutputItem.LocalShellAction] = ConfiguredDecoder.derived
  implicit val rrespOutMcpToolDecoder: Decoder[RRESP.OutputItem.McpListTools.Tool] = ConfiguredDecoder.derived
  implicit val rrespTopLogProbDecoder: Decoder[RRESP.OutputContent.TopLogProb] = ConfiguredDecoder.derived
  implicit val rrespLogProbDecoder: Decoder[RRESP.OutputContent.LogProb] = ConfiguredDecoder.derived
  implicit val rrespInputTokensDetailsDecoder: Decoder[RRESP.InputTokensDetails] = ConfiguredDecoder.derived
  implicit val rrespOutputTokensDetailsDecoder: Decoder[RRESP.OutputTokensDetails] = ConfiguredDecoder.derived
  implicit val rrespUsageDecoder: Decoder[RRESP.Usage] = ConfiguredDecoder.derived
  implicit val rrespInInputContentDecoder: Decoder[RRESP.InputItem.InputContent] = ConfiguredDecoder.derived
  implicit val rrespInOutputContentDecoder: Decoder[RRESP.InputItem.OutputContent] = ConfiguredDecoder.derived
  implicit val rrespInComputerActionDecoder: Decoder[RRESP.InputItem.ComputerCall.Action] = ConfiguredDecoder.derived
  implicit val rrespInWebActionDecoder: Decoder[RRESP.InputItem.WebSearchCall.Action] = ConfiguredDecoder.derived
  implicit val rrespInCodeOutputDecoder: Decoder[RRESP.InputItem.CodeInterpreterCall.Output] = ConfiguredDecoder.derived
  implicit val rrespOutComputerActionDecoder: Decoder[RRESP.OutputItem.ComputerCall.Action] = ConfiguredDecoder.derived
  implicit val rrespOutWebActionDecoder: Decoder[RRESP.OutputItem.WebSearchCall.Action] = ConfiguredDecoder.derived
  implicit val rrespOutCodeInterpreterOutputDecoder: Decoder[RRESP.OutputItem.CodeInterpreterOutput] = ConfiguredDecoder.derived
  implicit val rrespAnnotationDecoder: Decoder[RRESP.OutputContent.Annotation] = ConfiguredDecoder.derived
  implicit val rrespOutputContentDecoder: Decoder[RRESP.OutputContent] = ConfiguredDecoder.derived
  implicit val rrespFormatDecoder: Decoder[RRESP.Format] = ConfiguredDecoder.derived
  implicit val rrespInInputMessageDecoder: Decoder[RRESP.InputItem.InputMessage] = ConfiguredDecoder.derived
  implicit val rrespInOutputMessageDecoder: Decoder[RRESP.InputItem.OutputMessage] = ConfiguredDecoder.derived
  implicit val rrespInFileSearchCallDecoder: Decoder[RRESP.InputItem.FileSearchCall] = ConfiguredDecoder.derived
  implicit val rrespInComputerCallDecoder: Decoder[RRESP.InputItem.ComputerCall] = ConfiguredDecoder.derived
  implicit val rrespInComputerCallOutputDecoder: Decoder[RRESP.InputItem.ComputerCallOutput] = ConfiguredDecoder.derived
  implicit val rrespInWebSearchCallDecoder: Decoder[RRESP.InputItem.WebSearchCall] = ConfiguredDecoder.derived
  implicit val rrespInFunctionCallDecoder: Decoder[RRESP.InputItem.FunctionCall] = ConfiguredDecoder.derived
  implicit val rrespInFunctionCallOutputDecoder: Decoder[RRESP.InputItem.FunctionCallOutput] = ConfiguredDecoder.derived
  implicit val rrespInReasoningDecoder: Decoder[RRESP.InputItem.Reasoning] = ConfiguredDecoder.derived
  implicit val rrespInImageGenerationCallDecoder: Decoder[RRESP.InputItem.ImageGenerationCall] = ConfiguredDecoder.derived
  implicit val rrespInCodeInterpreterCallDecoder: Decoder[RRESP.InputItem.CodeInterpreterCall] = ConfiguredDecoder.derived
  implicit val rrespInLocalShellCallDecoder: Decoder[RRESP.InputItem.LocalShellCall] = ConfiguredDecoder.derived
  implicit val rrespInLocalShellCallOutputDecoder: Decoder[RRESP.InputItem.LocalShellCallOutput] = ConfiguredDecoder.derived
  implicit val rrespInMcpListToolsDecoder: Decoder[RRESP.InputItem.McpListTools] = ConfiguredDecoder.derived
  implicit val rrespInMcpApprovalRequestDecoder: Decoder[RRESP.InputItem.McpApprovalRequest] = ConfiguredDecoder.derived
  implicit val rrespInMcpApprovalResponseDecoder: Decoder[RRESP.InputItem.McpApprovalResponse] = ConfiguredDecoder.derived
  implicit val rrespInMcpToolCallDecoder: Decoder[RRESP.InputItem.McpToolCall] = ConfiguredDecoder.derived
  implicit val rrespInCustomToolCallOutputDecoder: Decoder[RRESP.InputItem.CustomToolCallOutput] = ConfiguredDecoder.derived
  implicit val rrespInCustomToolCallDecoder: Decoder[RRESP.InputItem.CustomToolCall] = ConfiguredDecoder.derived
  implicit val rrespInItemReferenceDecoder: Decoder[RRESP.InputItem.ItemReference] = ConfiguredDecoder.derived
  implicit val rrespInputItemDecoder: Decoder[RRESP.InputItem] = ConfiguredDecoder.derived
  implicit val rrespOutputItemDecoder: Decoder[RRESP.OutputItem] = ConfiguredDecoder.derived
  implicit val rrespTextConfigDecoder: Decoder[RRESP.TextConfig] = ConfiguredDecoder.derived
  implicit val responsesResponseBodyDecoder: Decoder[RRESP] = {
    import sttp.ai.core.json.CirceCodecs.emptyMapAsNone // local: empty `metadata` object -> None
    ConfiguredDecoder.derived
  }
  implicit val deleteModelResponseResponseDecoder: Decoder[DeleteModelResponseResponse] = ConfiguredDecoder.derived
}
