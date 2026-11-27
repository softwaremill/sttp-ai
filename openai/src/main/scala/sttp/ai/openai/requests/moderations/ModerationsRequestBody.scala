package sttp.ai.openai.requests.moderations

object ModerationsRequestBody {

  /** @param input
    *   The input text to classify.
    * @param model
    *   Specifies content moderation models of [[ModerationModel]].
    */
  case class ModerationsBody(input: String, model: Option[ModerationModel] = None)

  sealed abstract class ModerationModel(val value: String)
  object ModerationModel {
    case object OmniModeration20240926 extends ModerationModel("omni-moderation-2024-09-26")
    case object OmniModerationLatest extends ModerationModel("omni-moderation-latest")
    case class CustomModerationModel(customModerationModel: String) extends ModerationModel(customModerationModel)

    val values: Set[ModerationModel] =
      Set(OmniModeration20240926, OmniModerationLatest)
  }
}
