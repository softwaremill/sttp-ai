package sttp.openai.requests.responses
import sttp.openai.json.SnakePickle
import ujson.Str

sealed abstract class ResponsesModel(val value: String)

object ResponsesModel {

  implicit val responsesModelRW: SnakePickle.ReadWriter[ResponsesModel] = SnakePickle
    .readwriter[ujson.Value]
    .bimap[ResponsesModel](
      model => SnakePickle.writeJs(model.value),
      jsonValue =>
        SnakePickle.read[ujson.Value](jsonValue) match {
          case Str(value) =>
            byResponsesModelValue.getOrElse(value, CustomResponsesModel(value))
          case e => throw new Exception(s"Could not deserialize: $e")
        }
    )

  val values: Set[ResponsesModel] =
    Set(
      ChatGPT4oLatest,
      CodexMiniLatest,
      ComputerUsePreview,
      ComputerUsePreview20250311,
      GPT35Turbo,
      GPT35Turbo0125,
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
      GPT45Preview,
      GPT45Preview20250227,
      GPT4Turbo,
      GPT4Turbo20240409,
      GPT4TurboPreview,
      GPT4o,
      GPT4o20240513,
      GPT4o20240806,
      GPT4o20241120,
      GPT4oMini,
      GPT4oMini20240718,
      GPT5,
      GPT520250807,
      GPT5ChatLatest,
      GPT5Mini,
      GPT5Mini20250807,
      GPT5Nano,
      GPT5Nano20250807,
      GPTOSS120b,
      GPTOSS20b,
      O1,
      O120241217,
      O1Preview,
      O1Preview20240912,
      O1Pro,
      O1Pro20250319,
      O3,
      O320250416,
      O3DeepResearch,
      O3DeepResearch20250626,
      O3Mini,
      O3Mini20250131,
      O3Pro,
      O3Pro20250610,
      O4Mini,
      O4Mini20250416,
      O4MiniDeepResearch,
      O4MiniDeepResearch20250626
    )

  case object ChatGPT4oLatest extends ResponsesModel("chatgpt-4o-latest")
  case object CodexMiniLatest extends ResponsesModel("codex-mini-latest")
  case object ComputerUsePreview extends ResponsesModel("computer-use-preview")
  case object ComputerUsePreview20250311 extends ResponsesModel("computer-use-preview-2025-03-11")
  case object GPT35Turbo extends ResponsesModel("gpt-3.5-turbo")
  case object GPT35Turbo0125 extends ResponsesModel("gpt-3.5-turbo-0125")
  case object GPT35Turbo1106 extends ResponsesModel("gpt-3.5-turbo-1106")
  case object GPT35TurboInstruct extends ResponsesModel("gpt-3.5-turbo-instruct")
  case object GPT4 extends ResponsesModel("gpt-4")
  case object GPT40125Preview extends ResponsesModel("gpt-4-0125-preview")
  case object GPT40314 extends ResponsesModel("gpt-4-0314")
  case object GPT40613 extends ResponsesModel("gpt-4-0613")
  case object GPT41 extends ResponsesModel("gpt-4.1")
  case object GPT41106VisionPreview extends ResponsesModel("gpt-4-1106-vision-preview")
  case object GPT4120250414 extends ResponsesModel("gpt-4.1-2025-04-14")
  case object GPT41Mini extends ResponsesModel("gpt-4.1-mini")
  case object GPT41Mini20250414 extends ResponsesModel("gpt-4.1-mini-2025-04-14")
  case object GPT41Nano extends ResponsesModel("gpt-4.1-nano")
  case object GPT41Nano20250414 extends ResponsesModel("gpt-4.1-nano-2025-04-14")
  case object GPT45Preview extends ResponsesModel("gpt-4.5-preview")
  case object GPT45Preview20250227 extends ResponsesModel("gpt-4.5-preview-2025-02-27")
  case object GPT4Turbo extends ResponsesModel("gpt-4-turbo")
  case object GPT4Turbo20240409 extends ResponsesModel("gpt-4-turbo-2024-04-09")
  case object GPT4TurboPreview extends ResponsesModel("gpt-4-turbo-preview")
  case object GPT4o extends ResponsesModel("gpt-4o")
  case object GPT4o20240513 extends ResponsesModel("gpt-4o-2024-05-13")
  case object GPT4o20240806 extends ResponsesModel("gpt-4o-2024-08-06")
  case object GPT4o20241120 extends ResponsesModel("gpt-4o-2024-11-20")
  case object GPT4oMini extends ResponsesModel("gpt-4o-mini")
  case object GPT4oMini20240718 extends ResponsesModel("gpt-4o-mini-2024-07-18")
  case object GPT5 extends ResponsesModel("gpt-5")
  case object GPT520250807 extends ResponsesModel("gpt-5-2025-08-07")
  case object GPT5ChatLatest extends ResponsesModel("gpt-5-chat-latest")
  case object GPT5Mini extends ResponsesModel("gpt-5-mini")
  case object GPT5Mini20250807 extends ResponsesModel("gpt-5-mini-2025-08-07")
  case object GPT5Nano extends ResponsesModel("gpt-5-nano")
  case object GPT5Nano20250807 extends ResponsesModel("gpt-5-nano-2025-08-07")
  case object GPTOSS120b extends ResponsesModel("gpt-oss-120b")
  case object GPTOSS20b extends ResponsesModel("gpt-oss-20b")
  case object O1 extends ResponsesModel("o1")
  case object O120241217 extends ResponsesModel("o1-2024-12-17")
  case object O1Preview extends ResponsesModel("o1-preview")
  case object O1Preview20240912 extends ResponsesModel("o1-preview-2024-09-12")
  case object O1Pro extends ResponsesModel("o1-pro")
  case object O1Pro20250319 extends ResponsesModel("o1-pro-2025-03-19")
  case object O3 extends ResponsesModel("o3")
  case object O320250416 extends ResponsesModel("o3-2025-04-16")
  case object O3DeepResearch extends ResponsesModel("o3-deep-research")
  case object O3DeepResearch20250626 extends ResponsesModel("o3-deep-research-2025-06-26")
  case object O3Mini extends ResponsesModel("o3-mini")
  case object O3Mini20250131 extends ResponsesModel("o3-mini-2025-01-31")
  case object O3Pro extends ResponsesModel("o3-pro")
  case object O3Pro20250610 extends ResponsesModel("o3-pro-2025-06-10")
  case object O4Mini extends ResponsesModel("o4-mini")
  case object O4Mini20250416 extends ResponsesModel("o4-mini-2025-04-16")
  case object O4MiniDeepResearch extends ResponsesModel("o4-mini-deep-research")
  case object O4MiniDeepResearch20250626 extends ResponsesModel("o4-mini-deep-research-2025-06-26")
  case class CustomResponsesModel(customResponsesModel: String) extends ResponsesModel(customResponsesModel)

  private val byResponsesModelValue = values.map(model => model.value -> model).toMap
}
