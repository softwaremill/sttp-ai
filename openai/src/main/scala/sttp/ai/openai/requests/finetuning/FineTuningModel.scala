package sttp.ai.openai.requests.finetuning

sealed abstract class FineTuningModel(val value: String)

object FineTuningModel {

  case object GPT41 extends FineTuningModel("gpt-4.1")
  case object GPT4120250414 extends FineTuningModel("gpt-4.1-2025-04-14")
  case object GPT41Mini extends FineTuningModel("gpt-4.1-mini")
  case object GPT41Mini20250414 extends FineTuningModel("gpt-4.1-mini-2025-04-14")
  case object GPT4o extends FineTuningModel("gpt-4o")
  case object GPT4o20240806 extends FineTuningModel("gpt-4o-2024-08-06")
  case object GPT4o20241120 extends FineTuningModel("gpt-4o-2024-11-20")
  case object GPT4oMini extends FineTuningModel("gpt-4o-mini")
  case object GPT4oMini20240718 extends FineTuningModel("gpt-4o-mini-2024-07-18")
  case class CustomFineTuningModel(customFineTuningModel: String) extends FineTuningModel(customFineTuningModel)

  val values: Set[FineTuningModel] =
    Set(
      GPT41,
      GPT4120250414,
      GPT41Mini,
      GPT41Mini20250414,
      GPT4o,
      GPT4o20240806,
      GPT4o20241120,
      GPT4oMini,
      GPT4oMini20240718
    )
}
