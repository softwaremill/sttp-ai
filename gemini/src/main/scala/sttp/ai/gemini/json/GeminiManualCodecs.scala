package sttp.ai.gemini.json

import io.circe.{Codec, Decoder, Encoder}
import sttp.ai.gemini.models._

object GeminiManualCodecs {

  implicit val interactionStatusCodec: Codec[InteractionStatus] = Codec.from(
    Decoder.decodeString.map(InteractionStatus.fromString),
    Encoder.encodeString.contramap(_.value)
  )
}
