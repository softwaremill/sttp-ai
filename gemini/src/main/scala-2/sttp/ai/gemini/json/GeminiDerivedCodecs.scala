package sttp.ai.gemini.json

import io.circe.{Codec, Decoder, Encoder, Json}
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.circe.syntax._
import sttp.ai.gemini.models._
import sttp.ai.gemini.requests.InteractionRequest
import sttp.ai.gemini.responses._
import sttp.ai.core.json.CirceConfiguration.jsonConfiguration
import GeminiManualCodecs._

object GeminiDerivedCodecs {

  implicit val usageCodec: Codec[Usage] = deriveConfiguredCodec
  implicit val generationConfigCodec: Codec[GenerationConfig] = deriveConfiguredCodec
  implicit val safetySettingCodec: Codec[SafetySetting] = deriveConfiguredCodec

  // NOTE: keep this block in lockstep with its scala-2/scala-3 sibling file. The wrappers are duplicated here (not shared) deliberately:
  // GeminiManualCodecs must never reference this object, so class initialization is strictly one-way (this object -> manual codecs),
  // preventing the mutual <clinit> deadlock fixed for OpenAI in commit 55da3fc.

  private val KnownContentTypes = Set("text", "image", "audio", "video", "document")

  private val contentBaseCodec: Codec[Content] = deriveConfiguredCodec
  implicit val contentCodec: Codec[Content] = Codec.from(
    Decoder.instance { c =>
      c.get[Option[Json]]("type").map(_.flatMap(_.asString)).flatMap {
        case Some(t) if KnownContentTypes.contains(t) => contentBaseCodec(c)
        case _                                        => Right(Content.Unknown(c.value))
      }
    },
    Encoder.instance {
      case Content.Unknown(raw) => raw
      case other                => contentBaseCodec(other)
    }
  )

  private val KnownStepTypes = Set("user_input", "model_output", "function_call", "function_result", "thought")

  private val stepBaseCodec: Codec[Step] = deriveConfiguredCodec
  implicit val stepCodec: Codec[Step] = Codec.from(
    Decoder.instance { c =>
      c.get[Option[Json]]("type").map(_.flatMap(_.asString)).flatMap {
        case Some(t) if KnownStepTypes.contains(t) => stepBaseCodec(c) // real field-level failures propagate loudly
        case _                                     => Right(Step.Unknown(c.value))
      }
    },
    Encoder.instance {
      case Step.Unknown(raw) => raw
      case other             => stepBaseCodec(other)
    }
  )

  implicit val interactionInputCodec: Codec[InteractionInput] = Codec.from(
    Decoder.instance { c =>
      c.value.asString match {
        case Some(text) => Right(InteractionInput.TextInput(text))
        case None       => c.as[List[Step]].map(InteractionInput.StepsInput.apply)
      }
    },
    Encoder.instance {
      case InteractionInput.TextInput(text)   => Json.fromString(text)
      case InteractionInput.StepsInput(steps) => Json.fromValues(steps.map(_.asJson(stepCodec)))
    }
  )

  implicit val interactionRequestCodec: Codec[InteractionRequest] = deriveConfiguredCodec

  implicit val interactionResponseCodec: Codec[InteractionResponse] = deriveConfiguredCodec
  implicit val errorResponseCodec: Codec[ErrorResponse] = deriveConfiguredCodec
  implicit val streamErrorCodec: Codec[StreamError] = deriveConfiguredCodec
  implicit val streamMetadataCodec: Codec[StreamMetadata] = deriveConfiguredCodec
  implicit val interactionStreamEventCodec: Codec[InteractionStreamEvent] = deriveConfiguredCodec
}
