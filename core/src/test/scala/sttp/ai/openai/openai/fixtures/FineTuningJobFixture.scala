package sttp.openai.fixtures

import sttp.openai.requests.finetuning.Status.Running
import sttp.openai.requests.finetuning.{
  Dpo,
  Error,
  FineTuningJobResponse,
  FineTuningModel,
  Hyperparameters,
  Integration,
  Method,
  Supervised,
  Wandb
}

object FineTuningJobFixture {

  val jsonCreateFineTuneJobRequest: String = """{
                                            |  "model": "gpt-3.5-turbo-0125",
                                            |  "training_file": "file-train231",
                                            |  "suffix": "sml-model-name",
                                            |  "validation_file": "file-valid2",
                                            |  "integrations": [
                                            |    {
                                            |      "type": "wandb",
                                            |      "wandb": {
                                            |        "project": "sml-project",
                                            |        "name": "sml",
                                            |        "entity": "sml-entity",
                                            |        "tags": [
                                            |          "sml-tag"
                                            |        ]
                                            |      }
                                            |    }
                                            |  ],
                                            |  "seed": 3,
                                            |  "method": {
                                            |    "type": "supervised",
                                            |    "supervised": {
                                            |      "hyperparameters": {
                                            |        "batch_size": 2,
                                            |        "learning_rate_multiplier": 0.2,
                                            |        "n_epochs": 4
                                            |      }
                                            |    },
                                            |    "dpo": {
                                            |      "hyperparameters": {
                                            |        "batch_size": 3,
                                            |        "learning_rate_multiplier": 0.5,
                                            |        "n_epochs": 5,
                                            |        "beta": 0.7
                                            |      }
                                            |    }
                                            |  }
                                            |}""".stripMargin

  val jsonCreateFineTuneJobResponse: String = """{
                                             |  "id": "ft-id",
                                             |  "created_at": 1000,
                                             |  "error": {
                                             |    "code": "error-code",
                                             |    "message": "error-message",
                                             |    "param": "error-param"
                                             |  },
                                             |  "fine_tuned_model": "ft-model",
                                             |  "finished_at": 2000,
                                             |  "hyperparameters": {
                                             |    "batch_size": 2,
                                             |    "learning_rate_multiplier": 0.2,
                                             |    "n_epochs": 4
                                             |  },
                                             |  "model": "ft-model",
                                             |  "object": "ft-object",
                                             |  "organization_id": "ft-org-id",
                                             |  "result_files": [
                                             |    "ft-rf"
                                             |  ],
                                             |  "status": "running",
                                             |  "trained_tokens": 3000,
                                             |  "training_file": "ft-tf",
                                             |  "validation_file": "ft-vf",
                                             |  "integrations": [
                                             |    {
                                             |      "type": "wandb",
                                             |      "wandb": {
                                             |        "project": "sml-project",
                                             |        "name": "sml",
                                             |        "entity": "sml-entity",
                                             |        "tags": [
                                             |          "sml-tag"
                                             |        ]
                                             |      }
                                             |    }
                                             |  ],
                                             |  "seed": 1,
                                             |  "estimated_finish": 4000,
                                             |  "method": {
                                             |    "type": "supervised",
                                             |    "supervised": {
                                             |      "hyperparameters": {
                                             |        "batch_size": 2,
                                             |        "learning_rate_multiplier": 0.2,
                                             |        "n_epochs": 4
                                             |      }
                                             |    },
                                             |    "dpo": {
                                             |      "hyperparameters": {
                                             |        "batch_size": 3,
                                             |        "learning_rate_multiplier": 0.5,
                                             |        "n_epochs": 5,
                                             |        "beta": 0.7
                                             |      }
                                             |    }
                                             |  }
                                             |}""".stripMargin

  val jsonListFineTuningJobResponse: String = s"""{
                                           |  "object": "list",
                                           |  "data": [
                                           |  $jsonCreateFineTuneJobResponse
                                           |  ],
                                           |  "hasMore": false
                                           |}""".stripMargin

  val jsonListFineTuningJobEventsResponse: String = """{
                                                      |  "object": "list",
                                                      |  "data": [
                                                      |    {
                                                      |      "object": "fine_tuning.job.event",
                                                      |      "id": "ft-event-ddTJfwuMVpfLXseO0Am0Gqjm",
                                                      |      "created_at": 1721764800,
                                                      |      "level": "info",
                                                      |      "message": "Fine tuning job successfully completed",
                                                      |      "data": null,
                                                      |      "type": "message"
                                                      |    },
                                                      |    {
                                                      |      "object": "fine_tuning.job.event",
                                                      |      "id": "ft-event-tyiGuB72evQncpH87xe505Sv",
                                                      |      "created_at": 1721764800,
                                                      |      "level": "info",
                                                      |      "message": "New fine-tuned model created: ft:gpt-4o-mini:openai::7p4lURel",
                                                      |      "data": {},
                                                      |      "type": "message"
                                                      |    },
                                                      |    {
                                                      |      "object": "fine_tuning.job.event",
                                                      |      "id": "ft-AF1WoRqd3aJAHsqc9NY7iL8F",
                                                      |      "created_at": 1721764800,
                                                      |      "level": "error",
                                                      |      "message": "Fine-tuning job failed.",
                                                      |      "data": {
                                                      |        "job_id": "ft-AF1WoRqd3aJAHsqc9NY7iL8F",
                                                      |        "error": "Insufficient training data."
                                                      |      },
                                                      |      "type": "message"
                                                      |    }
                                                      |  ],
                                                      |  "has_more": true
                                                      |}""".stripMargin

  val jsonListFineTuningJobCheckpointsResponse: String = """{
                                                      |  "object": "list",
                                                      |  "data": [
                                                      |    {
                                                      |      "object": "fine_tuning.job.checkpoint",
                                                      |      "id": "ftckpt_zc4Q7MP6XxulcVzj4MZdwsAB",
                                                      |      "created_at": 1721764867,
                                                      |      "fine_tuned_model_checkpoint": "ft:gpt-4o-mini-2024-07-18:my-org:custom-suffix:96olL566:ckpt-step-2000",
                                                      |      "metrics": {
                                                      |        "full_valid_loss": 0.134,
                                                      |        "full_valid_mean_token_accuracy": 0.874,
                                                      |        "step": 0.123,
                                                      |        "train_loss": 0.346,
                                                      |        "train_mean_token_accuracy": 0.736,
                                                      |        "valid_loss": 0.654,
                                                      |        "valid_mean_token_accuracy": 0.738
                                                      |      },
                                                      |      "fine_tuning_job_id": "ftjob-abc123",
                                                      |      "step_number": 2000
                                                      |    }
                                                      |  ],
                                                      |  "first_id": "ftckpt_zc4Q7MP6XxulcVzj4MZdwsAB",
                                                      |  "last_id": "ftckpt_enQCFmOTGj3syEpYVhBRLTSy",
                                                      |  "has_more": true
                                                      |}""".stripMargin

  val fineTuningJobResponse: FineTuningJobResponse = FineTuningJobResponse(
    id = "ft-id",
    createdAt = 1000,
    error = Some(
      Error(
        code = "error-code",
        message = "error-message",
        param = Some("error-param")
      )
    ),
    fineTunedModel = Some("ft-model"),
    finishedAt = Some(2000),
    hyperparameters = Some(
      Hyperparameters(
        batchSize = Some(2),
        learningRateMultiplier = Some(0.2f),
        nEpochs = Some(4)
      )
    ),
    model = FineTuningModel.CustomFineTuningModel("ft-model"),
    `object` = "ft-object",
    organizationId = "ft-org-id",
    resultFiles = Seq("ft-rf"),
    status = Running,
    trainedTokens = Some(3000),
    trainingFile = "ft-tf",
    validationFile = Some("ft-vf"),
    integrations = Some(
      Seq(
        Integration(
          `type` = Integration.Wandb,
          wandb = Wandb(
            project = "sml-project",
            name = Some("sml"),
            entity = Some("sml-entity"),
            tags = Some(Seq("sml-tag"))
          )
        )
      )
    ),
    seed = 1,
    estimatedFinish = Some(4000),
    method = Method(
      `type` = Some(Method.Supervised),
      supervised = Some(
        Supervised(
          hyperparameters = Some(
            Hyperparameters(
              batchSize = Some(2),
              learningRateMultiplier = Some(0.2f),
              nEpochs = Some(4)
            )
          )
        )
      ),
      dpo = Some(
        Dpo(
          hyperparameters = Some(
            Hyperparameters(
              batchSize = Some(3),
              learningRateMultiplier = Some(0.5f),
              nEpochs = Some(5),
              beta = Some(0.7f)
            )
          )
        )
      )
    )
  )

}
