package sttp.ai.gemini.json

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import sttp.ai.gemini.models._
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.core.json.CirceConfiguration.jsonConfiguration
import GeminiManualCodecs._

object GeminiDerivedCodecs {

  implicit val usageCodec: Codec[Usage] = deriveConfiguredCodec
  implicit val contentCodec: Codec[Content] = deriveConfiguredCodec
  implicit val generationConfigCodec: Codec[GenerationConfig] = deriveConfiguredCodec
  implicit val safetySettingCodec: Codec[SafetySetting] = deriveConfiguredCodec
  implicit val stepCodec: Codec[Step] = deriveConfiguredCodec
  implicit val interactionRequestCodec: Codec[InteractionRequest] = deriveConfiguredCodec
}
