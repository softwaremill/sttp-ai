package sttp.ai.gemini.json

import io.circe.Codec
import io.circe.derivation.ConfiguredCodec
import sttp.ai.gemini.models._
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.responses._
import sttp.ai.core.json.CirceConfiguration.jsonConfiguration
import GeminiManualCodecs._

object GeminiDerivedCodecs {

  implicit val usageCodec: Codec[Usage] = ConfiguredCodec.derived
  implicit val contentCodec: Codec[Content] = ConfiguredCodec.derived
  implicit val generationConfigCodec: Codec[GenerationConfig] = ConfiguredCodec.derived
  implicit val safetySettingCodec: Codec[SafetySetting] = ConfiguredCodec.derived
  implicit val stepCodec: Codec[Step] = ConfiguredCodec.derived
  implicit val interactionRequestCodec: Codec[InteractionRequest] = ConfiguredCodec.derived

  implicit val interactionResponseCodec: Codec[InteractionResponse] = ConfiguredCodec.derived
  implicit val errorDetailCodec: Codec[ErrorDetail] = ConfiguredCodec.derived
  implicit val errorResponseCodec: Codec[ErrorResponse] = ConfiguredCodec.derived
  implicit val streamErrorCodec: Codec[StreamError] = ConfiguredCodec.derived
  implicit val streamMetadataCodec: Codec[StreamMetadata] = ConfiguredCodec.derived
  implicit val interactionStreamEventCodec: Codec[InteractionStreamEvent] = ConfiguredCodec.derived
}
