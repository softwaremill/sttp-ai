package sttp.openai.requests.finetuning

import sttp.openai.json.SnakePickle

/** @param batchSize
  *   Number of examples in each batch. A larger batch size means that model parameters are updated less frequently, but with lower
  *   variance.
  * @param learningRateMultiplier
  *   Scaling factor for the learning rate. A smaller learning rate may be useful to avoid overfitting.
  * @param nEpochs
  *   The number of epochs to train the model for. An epoch refers to one full cycle through the training dataset.
  * @param beta
  *   The beta value for the DPO method. A higher beta value will increase the weight of the penalty between the policy and reference model.
  */
case class Hyperparameters(
    batchSize: Option[Int] = None,
    learningRateMultiplier: Option[Float] = None,
    nEpochs: Option[Int] = None,
    beta: Option[Float] = None
)

object Hyperparameters {
  implicit val hyperparametersW: SnakePickle.ReadWriter[Hyperparameters] = SnakePickle.macroRW[Hyperparameters]
}
