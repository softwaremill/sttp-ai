package sttp.ai.claude.json

import io.circe.Codec
import io.circe.generic.extras.semiauto.{deriveConfiguredCodec, deriveEnumerationCodec}
import sttp.ai.claude.models._
import sttp.ai.claude.models.ContentBlock._
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses._
import sttp.ai.claude.responses.MessageStreamResponse._
import sttp.ai.core.json.CirceConfiguration.jsonConfiguration
import ClaudeManualCodecs._

object ClaudeDerivedCodecs {

  implicit val effortCodec: Codec[Effort] = deriveEnumerationCodec[Effort]

  implicit val usageCodec: Codec[Usage] = deriveConfiguredCodec
  implicit val errorDetailCodec: Codec[ErrorDetail] = deriveConfiguredCodec
  implicit val errorResponseCodec: Codec[ErrorResponse] = deriveConfiguredCodec
  implicit val modelDataCodec: Codec[ModelData] = deriveConfiguredCodec
  implicit val modelsResponseCodec: Codec[ModelsResponse] = deriveConfiguredCodec

  implicit val propertySchemaCodec: Codec[PropertySchema] = deriveConfiguredCodec
  implicit val toolInputSchemaCodec: Codec[ToolInputSchema] = deriveConfiguredCodec
  implicit val userLocationCodec: Codec[UserLocation] = deriveConfiguredCodec

  implicit val citationsConfigCodec: Codec[CitationsConfig] = deriveConfiguredCodec
  implicit val webSearchResultCodec: Codec[WebSearchResult] = deriveConfiguredCodec
  implicit val citationCodec: Codec[Citation] = deriveConfiguredCodec

  implicit val imageSourceCodec: Codec[ImageSource] = deriveConfiguredCodec
  implicit val documentSourceCodec: Codec[DocumentSource] = deriveConfiguredCodec

  implicit val toolCustomCodec: Codec[Tool.Custom] = deriveConfiguredCodec
  implicit val toolWebSearchCodec: Codec[Tool.WebSearch] = deriveConfiguredCodec

  implicit val contentBlockCodec: Codec[ContentBlock] = deriveConfiguredCodec

  implicit val messageStartDataCodec: Codec[MessageStreamResponse.MessageStartData] = deriveConfiguredCodec
  implicit val messageDeltaDataCodec: Codec[MessageStreamResponse.MessageDeltaData] = deriveConfiguredCodec
  implicit val contentDeltaCodec: Codec[MessageStreamResponse.ContentDelta] = deriveConfiguredCodec
  implicit val messageStreamResponseCodec: Codec[MessageStreamResponse] = deriveConfiguredCodec

  implicit val messageCodec: Codec[Message] = deriveConfiguredCodec
  implicit val outputConfigCodec: Codec[OutputConfig] = deriveConfiguredCodec
  implicit val messageRequestCodec: Codec[MessageRequest] = deriveConfiguredCodec
  implicit val messageResponseCodec: Codec[sttp.ai.claude.responses.MessageResponse] = deriveConfiguredCodec
}
