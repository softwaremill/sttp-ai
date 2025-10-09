package sttp.openai.requests.moderations

import sttp.openai.json.SnakePickle
import ujson.Str
object ModerationsRequestBody {

  /** @param input
    *   The input text to classify.
    * @param model
    *   Specifies content moderation models of [[ModerationModel]].
    */
  case class ModerationsBody(input: String, model: Option[ModerationModel] = None)

  object ModerationsBody {
    implicit val moderationsBodyWriter: SnakePickle.Writer[ModerationsBody] = SnakePickle.macroW[ModerationsBody]
  }
  sealed abstract class ModerationModel(val value: String)
  object ModerationModel {
    implicit val moderationsBodyWriter: SnakePickle.ReadWriter[ModerationModel] = SnakePickle
      .readwriter[ujson.Value]
      .bimap[ModerationModel](
        model => SnakePickle.writeJs(model.value),
        jsonValue =>
          SnakePickle.read[ujson.Value](jsonValue) match {
            case Str(value) =>
              byModerationModelValue.getOrElse(value, CustomModerationModel(value))
            case e => throw new Exception(s"Could not deserialize: $e")
          }
      )

    case object OmniModeration20240926 extends ModerationModel("omni-moderation-2024-09-26")
    case object OmniModerationLatest extends ModerationModel("omni-moderation-latest")
    case object TextModeration007 extends ModerationModel("text-moderation-007")
    case object TextModerationLatest extends ModerationModel("text-moderation-latest")
    case object TextModerationStable extends ModerationModel("text-moderation-stable")
    case class CustomModerationModel(customModerationModel: String) extends ModerationModel(customModerationModel)

    val values: Set[ModerationModel] =
      Set(
        OmniModeration20240926,
        OmniModerationLatest,
        TextModeration007,
        TextModerationLatest,
        TextModerationStable
      )

    private val byModerationModelValue = values.map(model => model.value -> model).toMap
  }
}
