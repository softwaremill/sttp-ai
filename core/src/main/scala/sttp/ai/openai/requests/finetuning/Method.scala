package sttp.openai.requests.finetuning

import sttp.openai.json.SnakePickle

/** @param `type`
  *   The type of method. Is either supervised or dpo.
  * @param supervised
  *   Configuration for the supervised fine-tuning method.
  * @param dpo
  *   Configuration for the DPO fine-tuning method.
  */
case class Method(
    `type`: Option[Type] = None,
    supervised: Option[Supervised] = None,
    dpo: Option[Dpo] = None
)

object Method {
  implicit val methodRW: SnakePickle.ReadWriter[Method] = SnakePickle.macroRW[Method]

  case object Supervised extends Type("supervised")

  case object Dpo extends Type("dpo")

  private val values: Set[Type] = Set(Supervised, Dpo)

  implicit val byTypeValue: Map[String, Type] = values.map(`type` => `type`.value -> `type`).toMap
}

/** @param hyperparameters
  *   The hyperparameters used for the fine-tuning job.
  */
case class Supervised(
    hyperparameters: Option[Hyperparameters] = None
)

object Supervised {
  implicit val supervisedRW: SnakePickle.ReadWriter[Supervised] = SnakePickle.macroRW[Supervised]
}

/** @param hyperparameters
  *   The hyperparameters used for the fine-tuning job.
  */
case class Dpo(
    hyperparameters: Option[Hyperparameters] = None
)

object Dpo {
  implicit val dpoRW: SnakePickle.ReadWriter[Dpo] = SnakePickle.macroRW[Dpo]
}
