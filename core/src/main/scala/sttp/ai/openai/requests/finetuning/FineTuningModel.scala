package sttp.openai.requests.finetuning
import sttp.openai.json.SnakePickle
import ujson.Str

sealed abstract class FineTuningModel(val value: String)

object FineTuningModel {

  implicit val fineTuningModelRW: SnakePickle.ReadWriter[FineTuningModel] = SnakePickle
    .readwriter[ujson.Value]
    .bimap[FineTuningModel](
      model => SnakePickle.writeJs(model.value),
      jsonValue =>
        SnakePickle.read[ujson.Value](jsonValue) match {
          case Str(value) =>
            byFineTuningModelValue.getOrElse(value, CustomFineTuningModel(value))
          case e => throw new Exception(s"Could not deserialize: $e")
        }
    )

  case object GPT35Turbo extends FineTuningModel("gpt-3.5-turbo")
  case object GPT35Turbo0125 extends FineTuningModel("gpt-3.5-turbo-0125")
  case object GPT35Turbo0613 extends FineTuningModel("gpt-3.5-turbo-0613")
  case object GPT35Turbo1106 extends FineTuningModel("gpt-3.5-turbo-1106")
  case object GPT35TurboInstruct extends FineTuningModel("gpt-3.5-turbo-instruct")
  case object GPT4 extends FineTuningModel("gpt-4")
  case object GPT40314 extends FineTuningModel("gpt-4-0314")
  case object GPT40613 extends FineTuningModel("gpt-4-0613")
  case object GPT41 extends FineTuningModel("gpt-4.1")
  case object GPT4120250414 extends FineTuningModel("gpt-4.1-2025-04-14")
  case object GPT41Mini extends FineTuningModel("gpt-4.1-mini")
  case object GPT41Mini20250414 extends FineTuningModel("gpt-4.1-mini-2025-04-14")
  case object GPT41Nano extends FineTuningModel("gpt-4.1-nano")
  case object GPT41Nano20250414 extends FineTuningModel("gpt-4.1-nano-2025-04-14")
  case object GPT4o extends FineTuningModel("gpt-4o")
  case object GPT4o20240513 extends FineTuningModel("gpt-4o-2024-05-13")
  case object GPT4o20240806 extends FineTuningModel("gpt-4o-2024-08-06")
  case object GPT4o20241120 extends FineTuningModel("gpt-4o-2024-11-20")
  case object GPT4oMini extends FineTuningModel("gpt-4o-mini")
  case object GPT4oMini20240718 extends FineTuningModel("gpt-4o-mini-2024-07-18")
  case object O4Mini extends FineTuningModel("o4-mini")
  case object O4Mini20250416 extends FineTuningModel("o4-mini-2025-04-16")
  case class CustomFineTuningModel(customFineTuningModel: String) extends FineTuningModel(customFineTuningModel)

  val values: Set[FineTuningModel] =
    Set(
      GPT35Turbo,
      GPT35Turbo0125,
      GPT35Turbo0613,
      GPT35Turbo1106,
      GPT35TurboInstruct,
      GPT4,
      GPT40314,
      GPT40613,
      GPT41,
      GPT4120250414,
      GPT41Mini,
      GPT41Mini20250414,
      GPT41Nano,
      GPT41Nano20250414,
      GPT4o,
      GPT4o20240513,
      GPT4o20240806,
      GPT4o20241120,
      GPT4oMini,
      GPT4oMini20240718,
      O4Mini,
      O4Mini20250416
    )

  private val byFineTuningModelValue = values.map(model => model.value -> model).toMap

}
