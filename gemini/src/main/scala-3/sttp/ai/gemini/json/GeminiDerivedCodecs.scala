package sttp.ai.gemini.json

import io.circe.{Codec, Decoder, Encoder, Json}
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

  // Derived codec for the known Step discriminators only; unrecognized `type` values fall back to Step.Unknown so that
  // decoding a response never fails just because the API introduced a new step type.
  private val stepBaseCodec: Codec[Step] = ConfiguredCodec.derived
  implicit val stepCodec: Codec[Step] = Codec.from(
    stepBaseCodec.or(Decoder[Json].map(Step.Unknown.apply)),
    Encoder.instance {
      case Step.Unknown(raw) => raw
      case other             => stepBaseCodec(other)
    }
  )

  implicit val interactionRequestCodec: Codec[InteractionRequest] = ConfiguredCodec.derived

  implicit val interactionResponseCodec: Codec[InteractionResponse] = ConfiguredCodec.derived
  implicit val errorResponseCodec: Codec[ErrorResponse] = ConfiguredCodec.derived
  implicit val streamErrorCodec: Codec[StreamError] = ConfiguredCodec.derived
  implicit val streamMetadataCodec: Codec[StreamMetadata] = ConfiguredCodec.derived
  implicit val interactionStreamEventCodec: Codec[InteractionStreamEvent] = ConfiguredCodec.derived
}
