package sttp.ai.openai.requests.responses

sealed abstract class ResponsesModel(val value: String)

object ResponsesModel {

  val values: Set[ResponsesModel] =
    Set(
      GPT41,
      GPT4120250414,
      GPT41Mini,
      GPT41Mini20250414,
      GPT4o,
      GPT4o20240806,
      GPT4o20241120,
      GPT4oMini,
      GPT4oMini20240718,
      GPT56Luna,
      GPT56Terra,
      GPT56Sol,
      GPT6Luna,
      GPT6Sol,
      GPT61Sol,
      GPT61Terra,
      GPT62Astra,
      GPT62Sol,
      GPTOSS120b,
      GPTOSS20b
    )

  case object GPT41 extends ResponsesModel("gpt-4.1")
  case object GPT4120250414 extends ResponsesModel("gpt-4.1-2025-04-14")
  case object GPT41Mini extends ResponsesModel("gpt-4.1-mini")
  case object GPT41Mini20250414 extends ResponsesModel("gpt-4.1-mini-2025-04-14")
  case object GPT4o extends ResponsesModel("gpt-4o")
  case object GPT4o20240806 extends ResponsesModel("gpt-4o-2024-08-06")
  case object GPT4o20241120 extends ResponsesModel("gpt-4o-2024-11-20")
  case object GPT4oMini extends ResponsesModel("gpt-4o-mini")
  case object GPT4oMini20240718 extends ResponsesModel("gpt-4o-mini-2024-07-18")
  case object GPT56Luna extends ResponsesModel("gpt-5.6-luna")
  case object GPT56Terra extends ResponsesModel("gpt-5.6-terra")
  case object GPT56Sol extends ResponsesModel("gpt-5.6-sol")
  case object GPT6Luna extends ResponsesModel("gpt-6-luna")
  case object GPT6Sol extends ResponsesModel("gpt-6-sol")
  case object GPT61Sol extends ResponsesModel("gpt-6.1-sol")
  case object GPT61Terra extends ResponsesModel("gpt-6.1-terra")
  case object GPT62Astra extends ResponsesModel("gpt-6.2-astra")
  case object GPT62Sol extends ResponsesModel("gpt-6.2-sol")
  case object GPTOSS120b extends ResponsesModel("gpt-oss-120b")
  case object GPTOSS20b extends ResponsesModel("gpt-oss-20b")
  case class CustomResponsesModel(customResponsesModel: String) extends ResponsesModel(customResponsesModel)
}
