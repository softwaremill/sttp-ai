package sttp.openai.requests.finetuning

import sttp.openai.json.SnakePickle
import ujson.Str

/** The fine_tuning.job object represents a fine-tuning job that has been created through the API.
  *
  * @param id
  *   The object identifier, which can be referenced in the API endpoints.
  * @param createdAt
  *   The Unix timestamp (in seconds) for when the fine-tuning job was created.
  * @param error
  *   For fine-tuning jobs that have failed, this will contain more information on the cause of the failure.
  * @param fineTunedModel
  *   The name of the fine-tuned model that is being created. The value will be null if the fine-tuning job is still running.
  * @param finishedAt
  *   The Unix timestamp (in seconds) for when the fine-tuning job was finished. The value will be null if the fine-tuning job is still
  *   running.
  * @param hyperparameters
  *   The hyperparameters used for the fine-tuning job. This value will only be returned when running supervised jobs.
  * @param model
  *   The base model that is being fine-tuned.
  * @param `object`
  *   The object type, which is always "fine_tuning.job".
  * @param organizationId
  *   The organization that owns the fine-tuning job.
  * @param resultFiles
  *   The compiled results file ID(s) for the fine-tuning job. You can retrieve the results with the Files API.
  * @param status
  *   The current status of the fine-tuning job, which can be either validating_files, queued, running, succeeded, failed, or cancelled.
  * @param trainedTokens
  *   The total number of billable tokens processed by this fine-tuning job. The value will be null if the fine-tuning job is still running.
  * @param trainingFile
  *   The file ID used for training. You can retrieve the training data with the Files API.
  * @param validationFile
  *   The file ID used for validation. You can retrieve the validation results with the Files API.
  * @param integrations
  *   A list of integrations to enable for this fine-tuning job.
  * @param seed
  *   The seed used for the fine-tuning job.
  * @param estimatedFinish
  *   The Unix timestamp (in seconds) for when the fine-tuning job is estimated to finish. The value will be null if the fine-tuning job is
  *   not running.
  * @param method
  *   The method used for fine-tuning.
  */
case class FineTuningJobResponse(
    id: String,
    createdAt: Int,
    error: Option[Error] = None,
    fineTunedModel: Option[String],
    finishedAt: Option[Int],
    hyperparameters: Option[Hyperparameters],
    model: FineTuningModel,
    `object`: String,
    organizationId: String,
    resultFiles: Seq[String],
    status: Status,
    trainedTokens: Option[Int] = None,
    trainingFile: String,
    validationFile: Option[String],
    integrations: Option[Seq[Integration]] = None,
    seed: Int,
    estimatedFinish: Option[Int] = None,
    method: Method
)

object FineTuningJobResponse {
  implicit val fineTuningResponseDataReader: SnakePickle.Reader[FineTuningJobResponse] = SnakePickle.macroR[FineTuningJobResponse]
}

/** @param code
  *   A machine-readable error code.
  * @param message
  *   A human-readable error message.
  * @param param
  *   The parameter that was invalid, usually training_file or validation_file. This field will be null if the failure was not
  *   parameter-specific.
  */
case class Error(
    code: String,
    message: String,
    param: Option[String] = None
)

object Error {
  implicit val errorReader: SnakePickle.Reader[Error] = SnakePickle.macroR[Error]
}

sealed abstract class Status(val value: String)

object Status {

  implicit val statusRW: SnakePickle.Reader[Status] = SnakePickle
    .reader[ujson.Value]
    .map[Status](jsonValue =>
      SnakePickle.read[ujson.Value](jsonValue) match {
        case Str(value) => byStatusValue.getOrElse(value, CustomStatus(value))
        case e          => throw new Exception(s"Could not deserialize: $e")
      }
    )

  case object ValidatingFiles extends Status("validating_files")

  case object Queued extends Status("queued")

  case object Running extends Status("running")

  case object Succeeded extends Status("succeeded")

  case object Failed extends Status("failed")

  case object Cancelled extends Status("cancelled")

  case class CustomStatus(customStatus: String) extends Status(customStatus)

  private val values: Set[Status] = Set(ValidatingFiles, Queued, Running, Succeeded, Failed, Cancelled)

  private val byStatusValue = values.map(status => status.value -> status).toMap

}

case class ListFineTuningJobResponse(
    `object`: String = "list",
    data: Seq[FineTuningJobResponse],
    hasMore: Boolean
)

object ListFineTuningJobResponse {
  implicit val listFineTuningResponseR: SnakePickle.Reader[ListFineTuningJobResponse] = SnakePickle.macroR[ListFineTuningJobResponse]
}

/** Fine-tuning job event object
  *
  * @param `object`
  *   The object type, which is always "fine_tuning.job.event".
  * @param id
  *   The object identifier.
  * @param createdAt
  *   The Unix timestamp (in seconds) for when the fine-tuning job was created.
  * @param level
  *   The log level of the event.
  * @param message
  *   The message of the event.
  * @param `type`
  *   The type of event.
  * @param data
  *   The data associated with the event.
  */
case class FineTuningJobEventResponse(
    `object`: String,
    id: String,
    createdAt: Int,
    level: String,
    message: String,
    `type`: String,
    data: Map[String, ujson.Value]
)

object FineTuningJobEventResponse {
  implicit val fineTuningJobEventResponseR: SnakePickle.Reader[FineTuningJobEventResponse] = SnakePickle.macroR[FineTuningJobEventResponse]
}

case class ListFineTuningJobEventResponse(
    `object`: String = "list",
    data: Seq[FineTuningJobEventResponse],
    hasMore: Boolean
)

object ListFineTuningJobEventResponse {
  implicit val listFineTuningJobEventResponseR: SnakePickle.Reader[ListFineTuningJobEventResponse] =
    SnakePickle.macroR[ListFineTuningJobEventResponse]
}

/** The fine_tuning.job.checkpoint object represents a model checkpoint for a fine-tuning job that is ready to use.
  *
  * @param id
  *   The checkpoint identifier, which can be referenced in the API endpoints.
  * @param createdAt
  *   The Unix timestamp (in seconds) for when the checkpoint was created.
  * @param fineTunedModelCheckpoint
  *   The name of the fine-tuned checkpoint model that is created.
  * @param stepNumber
  *   The step number that the checkpoint was created at.
  * @param metrics
  *   Metrics at the step number during the fine-tuning job.
  * @param fineTuningJobId
  *   The name of the fine-tuning job that this checkpoint was created from.
  * @param `object`
  *   The object type, which is always "fine_tuning.job.checkpoint".
  */
case class FineTuningJobCheckpointResponse(
    id: String,
    createdAt: Int,
    fineTunedModelCheckpoint: String,
    stepNumber: Int,
    metrics: Metrics,
    fineTuningJobId: String,
    `object`: String = "fine_tuning.job.checkpoint"
)

object FineTuningJobCheckpointResponse {
  implicit val fineTuningJobCheckpointResponseR: SnakePickle.Reader[FineTuningJobCheckpointResponse] =
    SnakePickle.macroR[FineTuningJobCheckpointResponse]
}

case class ListFineTuningJobCheckpointResponse(
    `object`: String = "list",
    data: Seq[FineTuningJobCheckpointResponse],
    firstId: String,
    lastId: String,
    hasMore: Boolean
)

object ListFineTuningJobCheckpointResponse {
  implicit val listFineTuningJobCheckpointResponseR: SnakePickle.Reader[ListFineTuningJobCheckpointResponse] =
    SnakePickle.macroR[ListFineTuningJobCheckpointResponse]
}

case class Metrics(
    step: Float,
    trainLoss: Float,
    trainMeanTokenAccuracy: Float,
    validLoss: Float,
    validMeanTokenAccuracy: Float,
    fullValidLoss: Float,
    fullValidMeanTokenAccuracy: Float
)

object Metrics {
  implicit val metricsR: SnakePickle.Reader[Metrics] = SnakePickle.macroR[Metrics]
}
