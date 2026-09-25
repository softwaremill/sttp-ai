package sttp.ai.jev.json

import io.circe.{Decoder, JsonObject}
import io.circe.derivation.ConfiguredDecoder
import sttp.ai.core.json.CirceConfiguration.jsonConfiguration
import sttp.ai.jev.{ModelInfo, Question, SystemOneResponse, Usage}

/** Decoders of the 2xx bodies. */
private[jev] object ResponseDecoders:
  private given Decoder[Usage] = ConfiguredDecoder.derived
  private given Decoder[ModelInfo] = ConfiguredDecoder.derived

  /** `GET /v1/models`: `{"models": [...]}`. */
  val models: Decoder[Seq[ModelInfo]] = Decoder.instance(_.get[List[ModelInfo]]("models"))

  /** `POST /v1/systemone`: answers are matched to `questions` by position. */
  def systemOne[A](questions: Seq[Question[A]], requestId: Option[String]): Decoder[SystemOneResponse[Seq[A]]] =
    Decoder.instance: cursor =>
      for
        model <- cursor.get[String]("model")
        usage <- cursor.get[Usage]("usage")
        answers <- cursor.get[JsonObject]("answers").flatMap(AnswerDecoder.decodeAll(questions, _))
      yield SystemOneResponse(answers, model, usage, requestId)
