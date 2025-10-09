package sttp.openai.requests.finetuning

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures.FineTuningJobFixture
import sttp.openai.json.SnakePickle
import sttp.openai.requests.finetuning.FineTuningModel.GPT35Turbo0125
import sttp.openai.utils.JsonUtils
import ujson.Str

class FineTuningDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given create fine tuning job request as case class" should "be properly serialized to Json" in {
    // given
    val givenRequest = FineTuningJobRequestBody(
      model = GPT35Turbo0125,
      trainingFile = "file-train231",
      suffix = Some("sml-model-name"),
      validationFile = Some("file-valid2"),
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
      seed = Some(3),
      method = Some(
        Method(
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
    )
    val jsonRequest: ujson.Value = ujson.read(FineTuningJobFixture.jsonCreateFineTuneJobRequest)
    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)
    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create fine tuning job response as Json" should "be properly deserialized to case class" in {
    // given
    val jsonResponse = FineTuningJobFixture.jsonCreateFineTuneJobResponse
    val expectedResponse: FineTuningJobResponse = FineTuningJobFixture.fineTuningJobResponse
    // when
    val deserializedJsonResponse: Either[Exception, FineTuningJobResponse] =
      JsonUtils.deserializeJsonSnake[FineTuningJobResponse].apply(jsonResponse)
    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

  "Given list fine tuning jobs response as Json" should "be properly deserialized to case class" in {
    // given
    val jsonResponse = FineTuningJobFixture.jsonListFineTuningJobResponse
    val expectedResponse: ListFineTuningJobResponse = ListFineTuningJobResponse(
      data = Seq(FineTuningJobFixture.fineTuningJobResponse),
      hasMore = false
    )
    // when
    val deserializedJsonResponse: Either[Exception, ListFineTuningJobResponse] =
      JsonUtils.deserializeJsonSnake[ListFineTuningJobResponse].apply(jsonResponse)

    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

  "Given list fine tuning job events response as Json" should "be properly deserialized to case class" in {
    // given
    val jsonResponse = FineTuningJobFixture.jsonListFineTuningJobEventsResponse
    val expectedResponse: ListFineTuningJobEventResponse = ListFineTuningJobEventResponse(
      data = Seq(
        FineTuningJobEventResponse(
          `object` = "fine_tuning.job.event",
          id = "ft-event-ddTJfwuMVpfLXseO0Am0Gqjm",
          createdAt = 1721764800,
          level = "info",
          message = "Fine tuning job successfully completed",
          data = null,
          `type` = "message"
        ),
        FineTuningJobEventResponse(
          `object` = "fine_tuning.job.event",
          id = "ft-event-tyiGuB72evQncpH87xe505Sv",
          createdAt = 1721764800,
          level = "info",
          message = "New fine-tuned model created: ft:gpt-4o-mini:openai::7p4lURel",
          data = Map(),
          `type` = "message"
        ),
        FineTuningJobEventResponse(
          `object` = "fine_tuning.job.event",
          id = "ft-AF1WoRqd3aJAHsqc9NY7iL8F",
          createdAt = 1721764800,
          level = "error",
          message = "Fine-tuning job failed.",
          data = Map("job_id" -> Str("ft-AF1WoRqd3aJAHsqc9NY7iL8F"), "error" -> Str("Insufficient training data.")),
          `type` = "message"
        )
      ),
      hasMore = true
    )
    // when
    val deserializedJsonResponse: Either[Exception, ListFineTuningJobEventResponse] =
      JsonUtils.deserializeJsonSnake[ListFineTuningJobEventResponse].apply(jsonResponse)
    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

  "Given list fine tuning job checkpoints response as Json" should "be properly deserialized to case class" in {
    // given
    val jsonResponse = FineTuningJobFixture.jsonListFineTuningJobCheckpointsResponse
    val expectedResponse: ListFineTuningJobCheckpointResponse = ListFineTuningJobCheckpointResponse(
      data = Seq(
        FineTuningJobCheckpointResponse(
          id = "ftckpt_zc4Q7MP6XxulcVzj4MZdwsAB",
          createdAt = 1721764867,
          fineTunedModelCheckpoint = "ft:gpt-4o-mini-2024-07-18:my-org:custom-suffix:96olL566:ckpt-step-2000",
          metrics = Metrics(
            fullValidLoss = 0.134f,
            fullValidMeanTokenAccuracy = 0.874f,
            step = 0.123f,
            trainLoss = 0.346f,
            trainMeanTokenAccuracy = 0.736f,
            validLoss = 0.654f,
            validMeanTokenAccuracy = 0.738f
          ),
          fineTuningJobId = "ftjob-abc123",
          stepNumber = 2000
        )
      ),
      firstId = "ftckpt_zc4Q7MP6XxulcVzj4MZdwsAB",
      lastId = "ftckpt_enQCFmOTGj3syEpYVhBRLTSy",
      hasMore = true
    )
    // when
    val deserializedJsonResponse: Either[Exception, ListFineTuningJobCheckpointResponse] =
      JsonUtils.deserializeJsonSnake[ListFineTuningJobCheckpointResponse].apply(jsonResponse)
    // then
    deserializedJsonResponse.value shouldBe expectedResponse
  }

}
