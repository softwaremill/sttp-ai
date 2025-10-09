package sttp.openai.requests.assistants

import sttp.openai.json.SnakePickle
import ujson.Str

sealed abstract class AssistantsModel(val value: String)

object AssistantsModel {

  implicit val assistantsModelRW: SnakePickle.ReadWriter[AssistantsModel] = SnakePickle
    .readwriter[ujson.Value]
    .bimap[AssistantsModel](
      model => SnakePickle.writeJs(model.value),
      jsonValue =>
        SnakePickle.read[ujson.Value](jsonValue) match {
          case Str(value) =>
            byAssistantsModelValue.getOrElse(value, CustomAssistantsModel(value))
          case e => throw new Exception(s"Could not deserialize: $e")
        }
    )

  val values: Set[AssistantsModel] =
    Set(
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
      O1,
      O120241217,
      O1Mini,
      O1Mini20240912,
      O1Preview,
      O1Preview20240912,
      O3Mini,
      O3Mini20250131
    )

  case object GPT4 extends AssistantsModel("gpt-4")
  case object GPT40125Preview extends AssistantsModel("gpt-4-0125-preview")
  case object GPT40314 extends AssistantsModel("gpt-4-0314")
  case object GPT40613 extends AssistantsModel("gpt-4-0613")
  case object GPT41 extends AssistantsModel("gpt-4.1")
  case object GPT41106VisionPreview extends AssistantsModel("gpt-4-1106-vision-preview")
  case object GPT4120250414 extends AssistantsModel("gpt-4.1-2025-04-14")
  case object GPT41Mini extends AssistantsModel("gpt-4.1-mini")
  case object GPT41Mini20250414 extends AssistantsModel("gpt-4.1-mini-2025-04-14")
  case object GPT41Nano extends AssistantsModel("gpt-4.1-nano")
  case object GPT41Nano20250414 extends AssistantsModel("gpt-4.1-nano-2025-04-14")
  case object GPT45Preview extends AssistantsModel("gpt-4.5-preview")
  case object GPT45Preview20250227 extends AssistantsModel("gpt-4.5-preview-2025-02-27")
  case object GPT4Turbo extends AssistantsModel("gpt-4-turbo")
  case object GPT4Turbo20240409 extends AssistantsModel("gpt-4-turbo-2024-04-09")
  case object GPT4TurboPreview extends AssistantsModel("gpt-4-turbo-preview")
  case object GPT4o extends AssistantsModel("gpt-4o")
  case object GPT4o20240513 extends AssistantsModel("gpt-4o-2024-05-13")
  case object GPT4o20240806 extends AssistantsModel("gpt-4o-2024-08-06")
  case object GPT4o20241120 extends AssistantsModel("gpt-4o-2024-11-20")
  case object GPT4oMini extends AssistantsModel("gpt-4o-mini")
  case object GPT4oMini20240718 extends AssistantsModel("gpt-4o-mini-2024-07-18")
  case object O1 extends AssistantsModel("o1")
  case object O120241217 extends AssistantsModel("o1-2024-12-17")
  case object O1Mini extends AssistantsModel("o1-mini")
  case object O1Mini20240912 extends AssistantsModel("o1-mini-2024-09-12")
  case object O1Preview extends AssistantsModel("o1-preview")
  case object O1Preview20240912 extends AssistantsModel("o1-preview-2024-09-12")
  case object O3Mini extends AssistantsModel("o3-mini")
  case object O3Mini20250131 extends AssistantsModel("o3-mini-2025-01-31")
  case class CustomAssistantsModel(customAssistantsModel: String) extends AssistantsModel(customAssistantsModel)

  private val byAssistantsModelValue = values.map(model => model.value -> model).toMap
}
