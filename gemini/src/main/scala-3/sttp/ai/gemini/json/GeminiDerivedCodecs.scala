package sttp.ai.gemini.json

import io.circe.Codec
import io.circe.derivation.ConfiguredCodec
import sttp.ai.gemini.models._
import sttp.ai.core.json.CirceConfiguration.jsonConfiguration
import GeminiManualCodecs._

object GeminiDerivedCodecs {

  implicit val usageCodec: Codec[Usage] = ConfiguredCodec.derived
  implicit val contentCodec: Codec[Content] = ConfiguredCodec.derived
}
