package sttp.ai.claude.json

import io.circe.Codec
import io.circe.derivation.{ConfiguredCodec, ConfiguredEnumCodec}
import sttp.ai.claude.models._
import sttp.ai.claude.models.ContentBlock._
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses._
import sttp.ai.claude.responses.MessageStreamResponse._
import sttp.ai.core.json.CirceConfiguration.jsonConfiguration
import ClaudeManualCodecs._

object ClaudeDerivedCodecs {

  implicit val effortCodec: Codec[Effort] = ConfiguredEnumCodec.derived[Effort]

  implicit val usageCodec: Codec[Usage] = ConfiguredCodec.derived
  implicit val errorDetailCodec: Codec[ErrorDetail] = ConfiguredCodec.derived
  implicit val errorResponseCodec: Codec[ErrorResponse] = ConfiguredCodec.derived
  implicit val modelDataCodec: Codec[ModelData] = ConfiguredCodec.derived
  implicit val modelsResponseCodec: Codec[ModelsResponse] = ConfiguredCodec.derived

  implicit val propertySchemaCodec: Codec[PropertySchema] = ConfiguredCodec.derived
  implicit val toolInputSchemaCodec: Codec[ToolInputSchema] = ConfiguredCodec.derived
  implicit val userLocationCodec: Codec[UserLocation] = ConfiguredCodec.derived

  implicit val citationsConfigCodec: Codec[CitationsConfig] = ConfiguredCodec.derived
  implicit val webSearchResultCodec: Codec[WebSearchResult] = ConfiguredCodec.derived
  implicit val citationCodec: Codec[Citation] = ConfiguredCodec.derived

  implicit val imageSourceCodec: Codec[ImageSource] = ConfiguredCodec.derived
  implicit val documentSourceCodec: Codec[DocumentSource] = ConfiguredCodec.derived

  implicit val toolCustomCodec: Codec[Tool.Custom] = ConfiguredCodec.derived
  implicit val toolWebSearchCodec: Codec[Tool.WebSearch] = ConfiguredCodec.derived

  implicit val contentBlockCodec: Codec[ContentBlock] = ConfiguredCodec.derived

  implicit val messageStartDataCodec: Codec[MessageStreamResponse.MessageStartData] = ConfiguredCodec.derived
  implicit val messageDeltaDataCodec: Codec[MessageStreamResponse.MessageDeltaData] = ConfiguredCodec.derived
  implicit val contentDeltaCodec: Codec[MessageStreamResponse.ContentDelta] = ConfiguredCodec.derived
  implicit val messageStreamResponseCodec: Codec[MessageStreamResponse] = ConfiguredCodec.derived

  implicit val messageCodec: Codec[Message] = ConfiguredCodec.derived
  implicit val outputConfigCodec: Codec[OutputConfig] = ConfiguredCodec.derived
  implicit val messageRequestCodec: Codec[MessageRequest] = ConfiguredCodec.derived
  implicit val messageResponseCodec: Codec[sttp.ai.claude.responses.MessageResponse] = ConfiguredCodec.derived
  implicit val cacheControlCodec: Codec[CacheControl] = ConfiguredCodec.derived
}
