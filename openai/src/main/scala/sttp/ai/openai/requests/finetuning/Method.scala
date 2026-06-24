package sttp.ai.openai.requests.finetuning

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
  case object Supervised extends Type("supervised")
  case object Dpo extends Type("dpo")
}

/** @param hyperparameters
  *   The hyperparameters used for the fine-tuning job.
  */
case class Supervised(
    hyperparameters: Option[Hyperparameters] = None
)

/** @param hyperparameters
  *   The hyperparameters used for the fine-tuning job.
  */
case class Dpo(
    hyperparameters: Option[Hyperparameters] = None
)
