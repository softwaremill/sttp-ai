package sttp.ai.openai.json

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
import io.circe.syntax._
import sttp.ai.openai.requests.assistants.AssistantsModel
import sttp.ai.openai.requests.audio.speech.SpeechModel
import sttp.ai.openai.requests.audio.transcriptions.TranscriptionModel
import sttp.ai.openai.requests.audio.translations.TranslationModel
import sttp.ai.openai.requests.completions.CompletionsRequestBody.{CompletionModel, MultiplePrompt, Prompt, SinglePrompt}
import sttp.ai.openai.requests.completions.Stop
import sttp.ai.openai.requests.completions.Stop.{MultipleStop, SingleStop}
import sttp.ai.openai.requests.completions.chat.ChatRequestBody
import sttp.ai.openai.requests.completions.chat.FunctionCall
import sttp.ai.openai.requests.completions.chat.ToolCall
import sttp.ai.openai.requests.completions.chat.message.ToolResources
import sttp.ai.openai.requests.embeddings.EmbeddingsRequestBody.{EmbeddingsInput, EmbeddingsModel}
import sttp.ai.openai.requests.finetuning.FineTuningModel
import sttp.ai.openai.requests.finetuning.FineTuningModel.CustomFineTuningModel
import sttp.ai.openai.requests.finetuning.{Type => FtType}
import sttp.ai.openai.requests.images.Size
import sttp.ai.openai.requests.images.creation.ImageCreationRequestBody.ImageCreationModel
import sttp.ai.openai.requests.images.edit.ImageEditsModel
import sttp.ai.openai.requests.moderations.ModerationsRequestBody.ModerationModel
import sttp.ai.openai.requests.responses.{InputItemsListResponseBody, ResponsesModel, ResponsesRequestBody, ResponsesResponseBody}
import sttp.ai.openai.requests.responses.ResponsesModel.CustomResponsesModel
import sttp.ai.openai.requests.responses.{Tool => RespTool, ToolChoice => RespToolChoice}
import OpenAIDerivedCodecs._
import sttp.ai.openai.requests.caching.CacheRetentionPolicy

/** Hand-written circe-core codecs for OpenAI: sealed-trait dispatch (custom `"type"` discriminators), string enums, and a few special-case
  * codecs (e.g. `Option` collapsing, JSON keys that don't match a field's snake_case). Uses only circe-core so it is shared across Scala
  * 2.13 and 3. The configured (snake_case) derivations — including the plain case-class codecs and the dispatch codecs whose discriminator
  * matches the snake_case constructor names — live in [[OpenAIDerivedCodecs]] (version-specific), which imports this object.
  *
  * This object and [[OpenAIDerivedCodecs]] reference each other, so initialization order matters: to avoid a circular class-initialization
  * deadlock (which surfaces when two threads first-touch the two objects concurrently, e.g. parallel test suites), this object must never
  * *eagerly* trigger initialization of [[OpenAIDerivedCodecs]]. Every reference here to a derivation defined in [[OpenAIDerivedCodecs]]
  * (e.g. `toolResourcesCodec`) therefore sits inside a lazy `Encoder.instance`/`Decoder.instance` body, evaluated only on first use rather
  * than during class init. The net effect is a one-way init dependency: [[OpenAIDerivedCodecs]] depends on this object, never the reverse.
  */
object OpenAIManualCodecs {

  /** Injects a flat `"type"` discriminator into an encoded object (shared by the `responses` dispatch codecs). */
  private def tagged(tpe: String, json: Json): Json =
    json.asObject.fold(json)(o => Json.fromJsonObject(o.add("type", Json.fromString(tpe))))

  implicit val assistantsModelCodec: Codec[AssistantsModel] = Codec.from(
    Decoder[String].map(AssistantsModel.get),
    Encoder[String].contramap(_.value)
  )

  // --- audio/speech/SpeechRequestBody ---
  implicit val speechModelEncoder: Encoder[SpeechModel] = Encoder[String].contramap(_.value)

  // --- audio/transcriptions/TranscriptionModel ---
  implicit val transcriptionModelCodec: Codec[TranscriptionModel] = Codec.from(
    Decoder[String].map(TranscriptionModel.Custom(_)),
    Encoder[String].contramap(_.value)
  )

  // --- audio/translations/TranslationModel ---
  implicit val translationModelCodec: Codec[TranslationModel] = Codec.from(
    Decoder[String].map(TranslationModel.Custom(_)),
    Encoder[String].contramap(_.value)
  )

  // --- completions/CompletionsRequestBody ---
  implicit val completionModelCodec: Codec[CompletionModel] = Codec.from(
    Decoder[String].map(value =>
      CompletionModel.values.map(m => m.value -> m).toMap.getOrElse(value, CompletionModel.CustomCompletionModel(value))
    ),
    Encoder[String].contramap(_.value)
  )

  implicit val promptEncoder: Encoder[Prompt] = Encoder.instance {
    case SinglePrompt(value)    => value.asJson
    case MultiplePrompt(values) => values.asJson
  }

  // --- completions/Stop ---
  implicit val stopCodec: Codec[Stop] = Codec.from(
    Decoder[String].map(SingleStop(_)).or(Decoder[Seq[String]].map(MultipleStop(_)): Decoder[Stop]),
    Encoder.instance {
      case SingleStop(value)    => value.asJson
      case MultipleStop(values) => values.asJson
    }
  )

  // --- completions/chat/ChatRequestBody ---
  implicit val chatContentEncoder: Encoder[ChatRequestBody.Content] = Encoder.instance {
    case ChatRequestBody.SingleContent(value)    => value.asJson
    case ChatRequestBody.MultipartContent(value) => value.asJson
  }

  implicit val chatCompletionModelCodec: Codec[ChatRequestBody.ChatCompletionModel] = Codec.from(
    Decoder[String].map(v =>
      ChatRequestBody.ChatCompletionModel.values
        .map(m => m.value -> m)
        .toMap
        .getOrElse(v, ChatRequestBody.ChatCompletionModel.CustomChatCompletionModel(v))
    ),
    Encoder[String].contramap(_.value)
  )

  // --- completions/chat/ToolCall ---
  implicit val functionToolCallCodec: Codec[ToolCall.FunctionToolCall] = Codec.from(
    Decoder.instance(c =>
      for {
        id <- c.get[Option[String]]("id")
        function <- c.get[FunctionCall]("function")
      } yield ToolCall.FunctionToolCall(id, function)
    ),
    Encoder.instance { tc =>
      val base = Json.obj("type" -> "function".asJson, "function" -> tc.function.asJson)
      tc.id.fold(base)(idValue => base.mapObject(_.add("id", idValue.asJson)))
    }
  )

  implicit val toolCallCodec: Codec[ToolCall] = Codec.from(
    Decoder[ToolCall.FunctionToolCall].map(x => x: ToolCall),
    Encoder.instance { case functionToolCall: ToolCall.FunctionToolCall => functionToolCall.asJson }
  )

  // --- completions/chat/message/ToolResources ---
  implicit val toolResourcesOptionCodec: Codec[Option[ToolResources]] = Codec.from(
    Decoder.instance { c =>
      if (c.value.isNull) Right(None)
      else
        c.value.asObject match {
          case Some(o) if o.isEmpty => Right(None)
          case _                    => c.as[ToolResources](toolResourcesCodec).map(Some(_))
        }
    },
    Encoder.instance {
      case None     => Json.Null
      case Some(tr) => tr.asJson(toolResourcesCodec)
    }
  )

  // --- embeddings/EmbeddingsRequestBody ---
  implicit val embeddingsModelCodec: Codec[EmbeddingsModel] = Codec.from(
    Decoder[String].map(value =>
      EmbeddingsModel.values.map(m => m.value -> m).toMap.getOrElse(value, EmbeddingsModel.CustomEmbeddingsModel(value))
    ),
    Encoder[String].contramap(_.value)
  )

  implicit val embeddingsInputEncoder: Encoder[EmbeddingsInput] = Encoder.instance {
    case EmbeddingsInput.SingleInput(value)    => value.asJson
    case EmbeddingsInput.MultipleInput(values) => values.asJson
  }

  // --- finetuning/FineTuningModel ---
  implicit val fineTuningModelCodec: Codec[FineTuningModel] = Codec.from(
    Decoder[String].map(v => FineTuningModel.values.map(m => m.value -> m).toMap.getOrElse(v, CustomFineTuningModel(v))),
    Encoder[String].contramap(_.value)
  )

  // --- images/Size ---
  implicit val sizeCodec: Codec[Size] = Codec.from(
    Decoder[String].map(v => Size.values.find(_.value == v).getOrElse(Size.Custom(v))),
    Encoder[String].contramap(_.value)
  )

  // --- images/creation/ImageCreationRequestBody ---
  implicit val imageCreationModelEncoder: Encoder[ImageCreationModel] = Encoder[String].contramap(_.value)

  // --- images/edit/ImageEditsConfig ---
  implicit val imageEditsModelEncoder: Encoder[ImageEditsModel] = Encoder[String].contramap(_.value)

  // --- moderations/ModerationsRequestBody ---
  implicit val moderationModelCodec: Codec[ModerationModel] = Codec.from(
    Decoder[String].map(value =>
      ModerationModel.values.map(m => m.value -> m).toMap.getOrElse(value, ModerationModel.CustomModerationModel(value))
    ),
    Encoder[String].contramap(_.value)
  )

  // --- responses/ResponsesModel ---
  implicit val responsesModelCodec: Codec[ResponsesModel] = Codec.from(
    Decoder[String].map(v => ResponsesModel.values.map(m => m.value -> m).toMap.getOrElse(v, CustomResponsesModel(v))),
    Encoder[String].contramap(_.value)
  )

  // --- responses/InputItemsListResponseBody ---
  implicit val iilMessageDecoder: Decoder[InputItemsListResponseBody.InputItem.Message] =
    Decoder.instance(c =>
      c.as[InputItemsListResponseBody.InputItem.OutputMessage]
        .left
        .flatMap(_ => c.as[InputItemsListResponseBody.InputItem.InputMessage])
    )

  // --- responses/ResponsesRequestBody ---
  implicit val rrbInputMessageEncoder: Encoder[ResponsesRequestBody.Input.Message] = Encoder.instance {
    case m: ResponsesRequestBody.Input.InputMessage  => m.asJson
    case m: ResponsesRequestBody.Input.OutputMessage => m.asJson
  }

  implicit val rrbTextOrInputListEncoder: Encoder[Either[String, List[ResponsesRequestBody.Input]]] = Encoder.instance {
    case Left(value)  => Json.fromString(value)
    case Right(value) => value.asJson
  }

  // --- responses/ResponsesResponseBody ---
  implicit val rrespMessageDecoder: Decoder[ResponsesResponseBody.InputItem.Message] =
    Decoder.instance(c =>
      c.as[ResponsesResponseBody.InputItem.OutputMessage]
        .left
        .flatMap(_ => c.as[ResponsesResponseBody.InputItem.InputMessage])
    )

  implicit val rrespInputMessageContentDecoder: Decoder[Either[String, List[ResponsesResponseBody.InputItem.InputContent]]] =
    Decoder[String].either(Decoder[List[ResponsesResponseBody.InputItem.InputContent]])

  implicit val rrespInstructionsDecoder: Decoder[Either[String, List[ResponsesResponseBody.InputItem]]] =
    Decoder[String].either(Decoder[List[ResponsesResponseBody.InputItem]])

  // --- responses/Tool ---
  implicit val respWebSearchPreviewCodec: Codec[RespTool.WebSearchPreview] = Codec.from(
    Decoder.instance { c =>
      c.get[String]("type").flatMap {
        case "web_search_preview"            => c.as[RespTool.WebSearchPreview.DefaultWebSearchPreview]
        case "web_search_preview_2025_03_11" => c.as[RespTool.WebSearchPreview.WebSearchPreview20250311]
        case other                           => Left(DecodingFailure(s"Unknown web search preview type: $other", c.history))
      }
    },
    Encoder.instance {
      case d: RespTool.WebSearchPreview.DefaultWebSearchPreview  => tagged("web_search_preview", d.asJson)
      case w: RespTool.WebSearchPreview.WebSearchPreview20250311 => tagged("web_search_preview_2025_03_11", w.asJson)
    }
  )

  implicit val respRequireApprovalCodec: Codec[RespTool.Mcp.RequireApproval] = Codec.from(
    Decoder.instance { c =>
      c.value.asString match {
        case Some("always") => Right(RespTool.Mcp.RequireApproval.Always)
        case Some("never")  => Right(RespTool.Mcp.RequireApproval.Never)
        case Some(other)    => Left(DecodingFailure(s"Unknown require approval: $other", c.history))
        case None           => c.as[RespTool.Mcp.RequireApproval.Filter]
      }
    },
    Encoder.instance {
      case RespTool.Mcp.RequireApproval.Always         => Json.fromString("always")
      case RespTool.Mcp.RequireApproval.Never          => Json.fromString("never")
      case filter: RespTool.Mcp.RequireApproval.Filter => filter.asJson
    }
  )

  implicit val respAllowedToolsCodec: Codec[RespTool.Mcp.AllowedTools] = Codec.from(
    Decoder.instance { c =>
      c.as[List[String]]
        .map(RespTool.Mcp.AllowedTools.ToolList(_))
        .left
        .flatMap(_ => c.as[Map[String, Json]].map(RespTool.Mcp.AllowedTools.FilterObject(_)))
    },
    Encoder.instance {
      case RespTool.Mcp.AllowedTools.ToolList(tools)      => tools.asJson
      case RespTool.Mcp.AllowedTools.FilterObject(filter) => filter.asJson
    }
  )

  implicit val respContainerCodec: Codec[RespTool.CodeInterpreter.Container] = Codec.from(
    Decoder.instance { c =>
      c.value.asString match {
        case Some(id) => Right(RespTool.CodeInterpreter.Container.ContainerId(id))
        case None     => c.as[RespTool.CodeInterpreter.Container.ContainerAuto]
      }
    },
    Encoder.instance {
      case auto: RespTool.CodeInterpreter.Container.ContainerAuto => tagged("auto", auto.asJson)
      case RespTool.CodeInterpreter.Container.ContainerId(id)     => Json.fromString(id)
    }
  )

  implicit val respToolCodec: Codec[RespTool] = Codec.from(
    Decoder.instance { c =>
      c.get[String]("type").flatMap {
        case "function"                                             => c.as[RespTool.Function]
        case "file_search"                                          => c.as[RespTool.FileSearch]
        case "web_search_preview" | "web_search_preview_2025_03_11" => c.as[RespTool.WebSearchPreview]
        case "computer_use_preview"                                 => c.as[RespTool.ComputerUsePreview]
        case "mcp"                                                  => c.as[RespTool.Mcp]
        case "code_interpreter"                                     => c.as[RespTool.CodeInterpreter]
        case "image_generation"                                     => c.as[RespTool.ImageGeneration]
        case "local_shell"                                          => c.as[RespTool.LocalShell]
        case "custom"                                               => c.as[RespTool.Custom]
        case other                                                  => Left(DecodingFailure(s"Unknown tool type: $other", c.history))
      }
    },
    Encoder.instance {
      case t: RespTool.Function           => tagged("function", t.asJson)
      case t: RespTool.FileSearch         => tagged("file_search", t.asJson)
      case t: RespTool.WebSearchPreview   => t.asJson
      case t: RespTool.ComputerUsePreview => tagged("computer_use_preview", t.asJson)
      case t: RespTool.Mcp                => tagged("mcp", t.asJson)
      case t: RespTool.CodeInterpreter    => tagged("code_interpreter", t.asJson)
      case t: RespTool.ImageGeneration    => tagged("image_generation", t.asJson)
      case t: RespTool.LocalShell         => tagged("local_shell", t.asJson)
      case t: RespTool.Custom             => tagged("custom", t.asJson)
    }
  )

  // --- responses/ToolChoice ---
  implicit val respToolChoiceCodec: Codec[RespToolChoice] = Codec.from(
    Decoder.instance { c =>
      if (c.value.isString) c.as[RespToolChoice.ToolChoiceMode]
      else c.as[RespToolChoice.ToolChoiceObject]
    },
    Encoder.instance {
      case m: RespToolChoice.ToolChoiceMode   => m.asJson
      case o: RespToolChoice.ToolChoiceObject => o.asJson
    }
  )

  implicit def typeCodec(implicit byTypeValue: Map[String, FtType]): Codec[FtType] = Codec.from(
    Decoder[String].emap(value => byTypeValue.get(value).toRight(s"Could not deserialize: $value")),
    Encoder[String].contramap(_.value)
  )

  implicit val cacheRetentionPolicyCodec: Codec[CacheRetentionPolicy] = Codec.from(
    Decoder[String].emap {
      case "in_memory" => Right(CacheRetentionPolicy.InMemory)
      case "24h"       => Right(CacheRetentionPolicy.`24H`)
      case s           => Left(s"Unknown cache retention policy: $s")
    },
    Encoder[String].contramap {
      case CacheRetentionPolicy.InMemory => "in_memory"
      case CacheRetentionPolicy.`24H`    => "24h"
    }
  )
}
