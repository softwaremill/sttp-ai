package sttp.ai.openai.requests.assistants

sealed abstract class AssistantsModel(val value: String)

object AssistantsModel {

  def get(value: String): AssistantsModel =
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
    ).map(m => m.value -> m).toMap.getOrElse(value, CustomAssistantsModel(value))

  case object GPT41 extends AssistantsModel("gpt-4.1")
  case object GPT4120250414 extends AssistantsModel("gpt-4.1-2025-04-14")
  case object GPT41Mini extends AssistantsModel("gpt-4.1-mini")
  case object GPT41Mini20250414 extends AssistantsModel("gpt-4.1-mini-2025-04-14")
  case object GPT4o extends AssistantsModel("gpt-4o")
  case object GPT4o20240806 extends AssistantsModel("gpt-4o-2024-08-06")
  case object GPT4o20241120 extends AssistantsModel("gpt-4o-2024-11-20")
  case object GPT4oMini extends AssistantsModel("gpt-4o-mini")
  case object GPT4oMini20240718 extends AssistantsModel("gpt-4o-mini-2024-07-18")
  case class CustomAssistantsModel(customAssistantsModel: String) extends AssistantsModel(customAssistantsModel)
}
