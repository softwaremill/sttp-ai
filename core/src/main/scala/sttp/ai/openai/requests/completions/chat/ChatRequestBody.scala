package sttp.openai.requests.completions.chat

import sttp.apispec.Schema
import sttp.openai.json.{SerializationHelpers, SnakePickle}
import sttp.openai.requests.completions.Stop
import sttp.openai.requests.completions.chat.message.{Message, Tool, ToolChoice}
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema
import sttp.tapir.{Schema => TSchema}
import ujson._

object ChatRequestBody {

  sealed trait ResponseFormat

  object ResponseFormat {
    case object Text extends ResponseFormat
    case object JsonObject extends ResponseFormat
    @upickle.implicits.key("json_schema")
    case class JsonSchema(name: String, strict: Option[Boolean], schema: Option[Schema], description: Option[String]) extends ResponseFormat

    object JsonSchema {

      /** Create a JsonSchema response format with schema automatically generated from type T.
        *
        * @param name
        *   The name of the response format.
        * @param description
        *   A description of what the response format is for.
        * @param strict
        *   Whether to enable strict schema adherence.
        * @tparam T
        *   The type to generate schema from.
        * @return
        *   A JsonSchema response format with auto-generated schema.
        */
      def withTapirSchema[T: TSchema](name: String, description: Option[String] = None, strict: Option[Boolean] = None): JsonSchema = {
        val schema = TapirSchemaToJsonSchema(implicitly[TSchema[T]], markOptionsAsNullable = true)
        JsonSchema(name, strict, Some(schema), description)
      }
    }

    implicit private val schemaRW: SnakePickle.ReadWriter[Schema] = SchemaSupport.schemaRW

    // Use SerializationHelpers to automatically create nested discriminator structure
    // This creates: {"type": "json_schema", "json_schema": {...actual JsonSchema object...}}
    implicit val jsonSchemaW: SnakePickle.Writer[JsonSchema] =
      SerializationHelpers.withNestedDiscriminatorWriter("json_schema", "json_schema")(SnakePickle.macroW[JsonSchema])

    implicit val textW: SnakePickle.Writer[Text.type] = SerializationHelpers.caseObjectWithDiscriminatorWriter("text")

    implicit val jsonObjectW: SnakePickle.Writer[JsonObject.type] =
      SerializationHelpers.caseObjectWithDiscriminatorWriter("json_object")

    implicit val responseFormatW: SnakePickle.Writer[ResponseFormat] = SnakePickle
      .writer[Value]
      .comap {
        case text: Text.type             => SnakePickle.writeJs(text)
        case jsonObject: JsonObject.type => SnakePickle.writeJs(jsonObject)
        case jsonSchema: JsonSchema      => SnakePickle.writeJs(jsonSchema)
      }

  }

  /** @param messages
    *   A list of messages describing the conversation so far.
    * @param model
    *   ID of the model to use.
    * @param frequencyPenalty
    *   Number between -2.0 and 2.0. Positive values penalize new tokens based on their existing frequency in the text so far, decreasing
    *   the model's likelihood to repeat the same line verbatim.
    * @param logitBias
    *   Modify the likelihood of specified tokens appearing in the completion.
    * @param maxTokens
    *   The maximum number of tokens to generate in the chat completion.
    * @param n
    *   How many chat completion choices to generate for each input message.
    * @param presencePenalty
    *   Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they appear in the text so far, increasing the
    *   model's likelihood to talk about new topics.
    * @param responseFormat
    *   An object specifying the format that the model must output. Setting to {"type": "json_object"} enables JSON mode, which guarantees
    *   the message the model generates is valid JSON.
    * @param seed
    *   This feature is in Beta. If specified, our system will make a best effort to sample deterministically, such that repeated requests
    *   with the same seed and parameters should return the same result. Determinism is not guaranteed, and you should refer to the
    *   system_fingerprint response parameter to monitor changes in the backend.
    * @param stop
    *   Up to 4 sequences where the API will stop generating further tokens.
    * @param temperature
    *   What sampling temperature to use, between 0 and 2. Higher values like 0.8 will make the output more random, while lower values like
    *   0.2 will make it more focused and deterministic.
    * @param topP
    *   An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens with top_p
    *   probability mass. So 0.1 means only the tokens comprising the top 10% probability mass are considered.
    * @param tools
    *   A list of tools the model may call. Currently, only functions are supported as a tool. Use this to provide a list of functions the
    *   model may generate JSON inputs for.
    * @param toolChoice
    *   Controls which (if any) function is called by the model.
    * @param user
    *   A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
    * @param store
    *   Whether or not to store the output of this chat completion request for use in our model distillation or evals products.
    * @param reasoningEffort
    *   Constrains effort on reasoning for reasoning models. Currently supported values are low, medium, and high. Reducing reasoning effort
    *   can result in faster responses and fewer tokens used on reasoning in a response.
    * @param metadata
    *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object
    *   in a structured format, and querying for objects via API or the dashboard. Keys are strings with a maximum length of 64 characters.
    *   Values are strings with a maximum length of 512 characters.
    * @param logprobs
    *   Whether to return log probabilities of the output tokens or not. If true, returns the log probabilities of each output token
    *   returned in the content of message.
    * @param topLogprobs
    *   An integer between 0 and 20 specifying the number of most likely tokens to return at each token position, each with an associated
    *   log probability. logprobs must be set to true if this parameter is used.
    * @param maxCompletionTokens
    *   An upper bound for the number of tokens that can be generated for a completion, including visible output tokens and reasoning
    *   tokens.
    * @param modalities
    *   Output types that you would like the model to generate for this request. Most models are capable of generating text, which is the
    *   default: ["text"]. The gpt-4o-audio-preview model can also be used to generate audio. To request that this model generate both text
    *   and audio responses, you can use: ["text", "audio"].
    * @param serviceTier
    *   Specifies the latency tier to use for processing the request. This parameter is relevant for customers subscribed to the scale tier
    *   service:
    *   - If set to 'auto', and the Project is Scale tier enabled, the system will utilize scale tier credits until they are exhausted.
    *   - If set to 'auto', and the Project is not Scale tier enabled, the request will be processed using the default service tier with a
    *     lower uptime SLA and no latency guarantee.
    *   - If set to 'default', the request will be processed using the default service tier with a lower uptime SLA and no latency
    *     guarantee.
    *   - When not set, the default behavior is 'auto'.
    * @param parallelToolCalls
    *   Whether to enable parallel function calling during tool use.
    * @param streamOptions
    *   Options for streaming response. Only set this when you set stream: true.
    * @param prediction
    *   Configuration for a Predicted Output, which can greatly improve response times when large parts of the model response are known
    *   ahead of time. This is most common when you are regenerating a file with only minor changes to most of the content.
    * @param audio
    *   Parameters for audio output. Required when audio output is requested with modalities: ["audio"].
    */
  case class ChatBody(
      messages: Seq[Message],
      model: ChatCompletionModel,
      frequencyPenalty: Option[Double] = None,
      logitBias: Option[Map[String, Float]] = None,
      maxTokens: Option[Int] = None,
      n: Option[Int] = None,
      presencePenalty: Option[Double] = None,
      responseFormat: Option[ResponseFormat] = None,
      seed: Option[Int] = None,
      stop: Option[Stop] = None,
      temperature: Option[Double] = None,
      topP: Option[Double] = None,
      tools: Option[Seq[Tool]] = None,
      toolChoice: Option[ToolChoice] = None,
      user: Option[String] = None,
      store: Option[Boolean] = None,
      reasoningEffort: Option[ReasoningEffort] = None,
      metadata: Option[Map[String, String]] = None,
      logprobs: Option[Boolean] = None,
      topLogprobs: Option[Int] = None,
      maxCompletionTokens: Option[Int] = None,
      modalities: Option[Seq[String]] = None,
      serviceTier: Option[String] = None,
      parallelToolCalls: Option[Boolean] = None,
      streamOptions: Option[StreamOptions] = None,
      prediction: Option[Prediction] = None,
      audio: Option[Audio] = None
  )

  object ChatBody {
    def withStreaming(chatBody: ChatBody): ujson.Value = {
      val json = SnakePickle.writeJs(chatBody)
      json.obj("stream") = true
      json
    }

    implicit val chatRequestW: SnakePickle.Writer[ChatBody] = SnakePickle.macroW[ChatBody]
  }

  /** @param voice
    *   The voice the model uses to respond. Supported voices are ash, ballad, coral, sage, and verse (also supported but not recommended
    *   are alloy, echo, and shimmer; these voices are less expressive).
    * @param format
    *   Specifies the output audio format. Must be one of wav, mp3, flac, opus, or pcm16.
    */
  case class Audio(
      voice: Voice,
      format: Format
  )

  object Audio {
    implicit val audioW: SnakePickle.Writer[Audio] = SnakePickle.macroW[Audio]
  }

  sealed abstract class Voice(val value: String)

  object Voice {
    case object Ash extends Voice("ash")
    case object Ballad extends Voice("ballad")
    case object Coral extends Voice("coral")
    case object Sage extends Voice("sage")
    case object Verse extends Voice("verse")
    case object Alloy extends Voice("alloy")
    case object Echo extends Voice("echo")
    case object Shimmer extends Voice("shimmer")
    case class CustomVoice(customVoice: String) extends Voice(customVoice)

    implicit val voiceW: SnakePickle.Writer[Voice] = SnakePickle
      .writer[ujson.Value]
      .comap[Voice](voice => SnakePickle.writeJs(voice.value))
  }

  sealed abstract class Format(val value: String)

  object Format {
    case object Wav extends Format("wav")
    case object Mp3 extends Format("mp3")
    case object Flac extends Format("flac")
    case object Opus extends Format("opus")
    case object Pcm16 extends Format("pcm16")
    case class CustomFormat(customFormat: String) extends Format(customFormat)

    implicit val formatW: SnakePickle.Writer[Format] = SnakePickle
      .writer[ujson.Value]
      .comap[Format](format => SnakePickle.writeJs(format.value))
  }

  /** @param `type`
    *   The type of the predicted content you want to provide. This type is currently always content.
    * @param content
    *   The content that should be matched when generating a model response. If generated tokens would match this content, the entire model
    *   response can be returned much more quickly.
    */
  case class Prediction(
      `type`: String,
      content: Content
  )

  object Prediction {
    implicit val predictionW: SnakePickle.Writer[Prediction] = SnakePickle.macroW[Prediction]
  }

  sealed trait Content
  case class SingleContent(value: String) extends Content
  case class MultipartContent(value: Seq[ContentPart]) extends Content

  object Content {
    implicit val contentW: SnakePickle.Writer[Content] = SnakePickle
      .writer[ujson.Value]
      .comap[Content] {
        case SingleContent(value)    => SnakePickle.writeJs(value)
        case MultipartContent(value) => SnakePickle.writeJs(value)
      }
  }

  /** An array of content parts with a defined type. Supported options differ based on the model being used to generate the response. Can
    * contain text inputs.
    *
    * @param `type`
    *   The type of the content part.
    * @param text
    *   The text content.
    */
  case class ContentPart(
      `type`: String,
      text: String
  )

  object ContentPart {
    implicit val contentPartW: SnakePickle.Writer[ContentPart] = SnakePickle.macroW[ContentPart]
  }

  /** @param includeUsage
    *   If set, an additional chunk will be streamed before the data: [DONE] message. The usage field on this chunk shows the token usage
    *   statistics for the entire request, and the choices field will always be an empty array. All other chunks will also include a usage
    *   field, but with a null value.
    */
  case class StreamOptions(includeUsage: Option[Boolean] = None)

  object StreamOptions {
    implicit val streamOptionsW: SnakePickle.Writer[StreamOptions] = SnakePickle.macroW[StreamOptions]
  }

  sealed abstract class ReasoningEffort(val value: String)

  object ReasoningEffort {

    implicit val reasoningEffortW: SnakePickle.Writer[ReasoningEffort] = SnakePickle
      .writer[ujson.Value]
      .comap[ReasoningEffort](reasoningEffort => SnakePickle.writeJs(reasoningEffort.value))

    case object Low extends ReasoningEffort("low")

    case object Medium extends ReasoningEffort("medium")

    case object High extends ReasoningEffort("high")

    case class CustomReasoningEffort(customReasoningEffort: String) extends ReasoningEffort(customReasoningEffort)

  }

  sealed abstract class ChatCompletionModel(val value: String)

  object ChatCompletionModel {
    implicit val chatCompletionModelRW: SnakePickle.ReadWriter[ChatCompletionModel] = SnakePickle
      .readwriter[ujson.Value]
      .bimap[ChatCompletionModel](
        model => SnakePickle.writeJs(model.value),
        jsonValue =>
          SnakePickle.read[ujson.Value](jsonValue) match {
            case Str(value) =>
              byChatModelValue.getOrElse(value, CustomChatCompletionModel(value))
            case e => throw new Exception(s"Could not deserialize: $e")
          }
      )

    case object ChatGPT4oLatest extends ChatCompletionModel("chatgpt-4o-latest")
    case object GPT35Turbo extends ChatCompletionModel("gpt-3.5-turbo")
    case object GPT35Turbo0125 extends ChatCompletionModel("gpt-3.5-turbo-0125")
    case object GPT35Turbo0301 extends ChatCompletionModel("gpt-3.5-turbo-0301")
    case object GPT35Turbo1106 extends ChatCompletionModel("gpt-3.5-turbo-1106")
    case object GPT35TurboInstruct extends ChatCompletionModel("gpt-3.5-turbo-instruct")
    case object GPT4 extends ChatCompletionModel("gpt-4")
    case object GPT40125Preview extends ChatCompletionModel("gpt-4-0125-preview")
    case object GPT40314 extends ChatCompletionModel("gpt-4-0314")
    case object GPT40613 extends ChatCompletionModel("gpt-4-0613")
    case object GPT41 extends ChatCompletionModel("gpt-4.1")
    case object GPT41106VisionPreview extends ChatCompletionModel("gpt-4-1106-vision-preview")
    case object GPT4120250414 extends ChatCompletionModel("gpt-4.1-2025-04-14")
    case object GPT41Mini extends ChatCompletionModel("gpt-4.1-mini")
    case object GPT41Mini20250414 extends ChatCompletionModel("gpt-4.1-mini-2025-04-14")
    case object GPT41Nano extends ChatCompletionModel("gpt-4.1-nano")
    case object GPT41Nano20250414 extends ChatCompletionModel("gpt-4.1-nano-2025-04-14")
    case object GPT432k extends ChatCompletionModel("gpt-4-32k")
    case object GPT432k0314 extends ChatCompletionModel("gpt-4-32k-0314")
    case object GPT45Preview extends ChatCompletionModel("gpt-4.5-preview")
    case object GPT45Preview20250227 extends ChatCompletionModel("gpt-4.5-preview-2025-02-27")
    case object GPT4Turbo extends ChatCompletionModel("gpt-4-1106-preview")
    case object GPT4Turbo20240409 extends ChatCompletionModel("gpt-4-turbo-2024-04-09")
    case object GPT4TurboPreview extends ChatCompletionModel("gpt-4-turbo-preview")
    case object GPT4o extends ChatCompletionModel("gpt-4o")
    case object GPT4o20240513 extends ChatCompletionModel("gpt-4o-2024-05-13")
    case object GPT4o20240806 extends ChatCompletionModel("gpt-4o-2024-08-06")
    case object GPT4o20241120 extends ChatCompletionModel("gpt-4o-2024-11-20")
    case object GPT4oAudioPreview extends ChatCompletionModel("gpt-4o-audio-preview")
    case object GPT4oAudioPreview20241001 extends ChatCompletionModel("gpt-4o-audio-preview-2024-10-01")
    case object GPT4oAudioPreview20241217 extends ChatCompletionModel("gpt-4o-audio-preview-2024-12-17")
    case object GPT4oAudioPreview20250603 extends ChatCompletionModel("gpt-4o-audio-preview-2025-06-03")
    case object GPT4oMini extends ChatCompletionModel("gpt-4o-mini")
    case object GPT4oMini20240718 extends ChatCompletionModel("gpt-4o-mini-2024-07-18")
    case object GPT4oMiniAudioPreview extends ChatCompletionModel("gpt-4o-mini-audio-preview")
    case object GPT4oMiniAudioPreview20241217 extends ChatCompletionModel("gpt-4o-mini-audio-preview-2024-12-17")
    case object GPT4oMiniSearchPreview extends ChatCompletionModel("gpt-4o-mini-search-preview")
    case object GPT4oMiniSearchPreview20250311 extends ChatCompletionModel("gpt-4o-mini-search-preview-2025-03-11")
    case object GPT4oSearchPreview extends ChatCompletionModel("gpt-4o-search-preview")
    case object GPT4oSearchPreview20250311 extends ChatCompletionModel("gpt-4o-search-preview-2025-03-11")
    case object GPT5 extends ChatCompletionModel("gpt-5")
    case object GPT520250807 extends ChatCompletionModel("gpt-5-2025-08-07")
    case object GPT5ChatLatest extends ChatCompletionModel("gpt-5-chat-latest")
    case object GPT5Mini extends ChatCompletionModel("gpt-5-mini")
    case object GPT5Mini20250807 extends ChatCompletionModel("gpt-5-mini-2025-08-07")
    case object GPT5Nano extends ChatCompletionModel("gpt-5-nano")
    case object GPT5Nano20250807 extends ChatCompletionModel("gpt-5-nano-2025-08-07")
    case object O1 extends ChatCompletionModel("o1")
    case object O120241217 extends ChatCompletionModel("o1-2024-12-17")
    case object O1Mini extends ChatCompletionModel("o1-mini")
    case object O1Mini20240912 extends ChatCompletionModel("o1-mini-2024-09-12")
    case object O1Preview extends ChatCompletionModel("o1-preview")
    case object O1Preview20240912 extends ChatCompletionModel("o1-preview-2024-09-12")
    case object O3 extends ChatCompletionModel("o3")
    case object O320250416 extends ChatCompletionModel("o3-2025-04-16")
    case object O3Mini extends ChatCompletionModel("o3-mini")
    case object O3Mini20250131 extends ChatCompletionModel("o3-mini-2025-01-31")
    case object O4Mini extends ChatCompletionModel("o4-mini")
    case object O4Mini20250416 extends ChatCompletionModel("o4-mini-2025-04-16")
    case class CustomChatCompletionModel(customChatCompletionModel: String) extends ChatCompletionModel(customChatCompletionModel)

    val values: Set[ChatCompletionModel] =
      Set(
        ChatGPT4oLatest,
        GPT35Turbo,
        GPT35Turbo0125,
        GPT35Turbo0301,
        GPT35Turbo1106,
        GPT35TurboInstruct,
        GPT4,
        GPT40125Preview,
        GPT40314,
        GPT40613,
        GPT41,
        GPT41106VisionPreview,
        GPT4120250414,
        GPT41Mini,
        GPT41Mini20250414,
        GPT41Nano,
        GPT41Nano20250414,
        GPT432k,
        GPT432k0314,
        GPT45Preview,
        GPT45Preview20250227,
        GPT4Turbo,
        GPT4Turbo20240409,
        GPT4TurboPreview,
        GPT4o,
        GPT4o20240513,
        GPT4o20240806,
        GPT4o20241120,
        GPT4oAudioPreview,
        GPT4oAudioPreview20241001,
        GPT4oAudioPreview20241217,
        GPT4oAudioPreview20250603,
        GPT4oMini,
        GPT4oMini20240718,
        GPT4oMiniAudioPreview,
        GPT4oMiniAudioPreview20241217,
        GPT4oMiniSearchPreview,
        GPT4oMiniSearchPreview20250311,
        GPT4oSearchPreview,
        GPT4oSearchPreview20250311,
        GPT5,
        GPT520250807,
        GPT5ChatLatest,
        GPT5Mini,
        GPT5Mini20250807,
        GPT5Nano,
        GPT5Nano20250807,
        O1,
        O120241217,
        O1Mini,
        O1Mini20240912,
        O1Preview,
        O1Preview20240912,
        O3,
        O320250416,
        O3Mini,
        O3Mini20250131,
        O4Mini,
        O4Mini20250416
      )

    private val byChatModelValue = values.map(model => model.value -> model).toMap
  }

  /** @param metadata
    *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object
    *   in a structured format, and querying for objects via API or the dashboard. Keys are strings with a maximum length of 64 characters.
    *   Values are strings with a maximum length of 512 characters.
    */
  case class UpdateChatCompletionRequestBody(metadata: Map[String, String])

  object UpdateChatCompletionRequestBody {
    implicit val updateChatCompletionRequestBodyW: SnakePickle.Writer[UpdateChatCompletionRequestBody] =
      SnakePickle.macroW[UpdateChatCompletionRequestBody]
  }

}
