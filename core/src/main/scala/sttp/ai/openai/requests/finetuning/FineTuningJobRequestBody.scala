package sttp.openai.requests.finetuning

import sttp.openai.json.SnakePickle

/** @param model
  *   The name of the model to fine-tune. You can select one of the supported models
  *   [[https://platform.openai.com/docs/guides/fine-tuning#which-models-can-be-fine-tuned]].
  * @param trainingFile
  *   The ID of an uploaded file that contains training data. See upload file for how to upload a file. Your dataset must be formatted as a
  *   JSONL file. Additionally, you must upload your file with the purpose fine-tune. The contents of the file should differ depending on if
  *   the model uses the chat, completions format, or if the fine-tuning method uses the preference format. See the fine-tuning guide for
  *   more details.
  * @param suffix
  *   A string of up to 64 characters that will be added to your fine-tuned model name. For example, a suffix of "custom-model-name" would
  *   produce a model name like ft:gpt-4o-mini:openai:custom-model-name:7p4lURel.
  * @param validationFile
  *   The ID of an uploaded file that contains validation data. If you provide this file, the data is used to generate validation metrics
  *   periodically during fine-tuning. These metrics can be viewed in the fine-tuning results file. The same data should not be present in
  *   both train and validation files. Your dataset must be formatted as a JSONL file. You must upload your file with the purpose fine-tune.
  *   See the fine-tuning guide for more details.
  * @param integrations
  *   A list of integrations to enable for your fine-tuning job.
  * @param seed
  *   The seed controls the reproducibility of the job. Passing in the same seed and job parameters should produce the same results, but may
  *   differ in rare cases. If a seed is not specified, one will be generated for you.
  * @param method
  *   The method used for fine-tuning.
  * @param metadata
  *   Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object in
  *   a structured format, and querying for objects via API or the dashboard. Keys are strings with a maximum length of 64 characters.
  *   Values are strings with a maximum length of 512 characters.
  */
case class FineTuningJobRequestBody(
    model: FineTuningModel,
    trainingFile: String,
    suffix: Option[String] = None,
    validationFile: Option[String] = None,
    integrations: Option[Seq[Integration]] = None,
    seed: Option[Int] = None,
    method: Option[Method] = None,
    metadata: Option[Map[String, String]] = None
)
object FineTuningJobRequestBody {
  implicit val fineTuningRequestBodyWriter: SnakePickle.Writer[FineTuningJobRequestBody] = SnakePickle.macroW[FineTuningJobRequestBody]
}
