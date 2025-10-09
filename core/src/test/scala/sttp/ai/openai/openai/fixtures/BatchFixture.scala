package sttp.openai.fixtures

import sttp.openai.requests.batch.{BatchResponse, RequestCounts}

object BatchFixture {

  val jsonCreateBatchRequest: String = """{
                                 |  "input_file_id": "file-id",
                                 |  "endpoint": "/v1/chat/completions",
                                 |  "completion_window": "24h",
                                 |  "metadata": {
                                 |    "key1": "value1",
                                 |    "key2": "value2"
                                 |  }
                                 |}""".stripMargin

  val jsonCreateBatchResponse: String = """{
                                  |  "id": "batch_abc123",
                                  |  "object": "batch",
                                  |  "endpoint": "/v1/completions",
                                  |  "errors": null,
                                  |  "input_file_id": "file-abc123",
                                  |  "completion_window": "24h",
                                  |  "status": "completed",
                                  |  "output_file_id": "file-cvaTdG",
                                  |  "error_file_id": "file-HOWS94",
                                  |  "created_at": 1711471533,
                                  |  "in_progress_at": 1711471538,
                                  |  "expires_at": 1711557933,
                                  |  "finalizing_at": 1711493133,
                                  |  "completed_at": 1711493163,
                                  |  "failed_at": null,
                                  |  "expired_at": null,
                                  |  "cancelling_at": null,
                                  |  "cancelled_at": null,
                                  |  "request_counts": {
                                  |    "total": 100,
                                  |    "completed": 95,
                                  |    "failed": 5
                                  |  },
                                  |  "metadata": {
                                  |    "customer_id": "user_123456789",
                                  |    "batch_description": "Nightly eval job"
                                  |  }
                                  |}""".stripMargin

  val jsonListBatchResponse: String = s"""{
                                   |  "object": "list",
                                   |  "data": [$jsonCreateBatchResponse],
                                   |  "first_id": "ftckpt_zc4Q7MP6XxulcVzj4MZdwsAB",
                                   |  "last_id": "ftckpt_enQCFmOTGj3syEpYVhBRLTSy",
                                   |  "has_more": true
                                   |}""".stripMargin

  val batchResponse: BatchResponse = BatchResponse(
    id = "batch_abc123",
    endpoint = "/v1/completions",
    errors = None,
    inputFileId = "file-abc123",
    completionWindow = "24h",
    status = "completed",
    outputFileId = Some("file-cvaTdG"),
    errorFileId = Some("file-HOWS94"),
    createdAt = 1711471533,
    inProgressAt = Some(1711471538),
    expiresAt = Some(1711557933),
    finalizingAt = Some(1711493133),
    completedAt = Some(1711493163),
    failedAt = None,
    expiredAt = None,
    cancellingAt = None,
    cancelledAt = None,
    requestCounts = Some(RequestCounts(total = 100, completed = 95, failed = 5)),
    metadata = Some(Map("customer_id" -> "user_123456789", "batch_description" -> "Nightly eval job"))
  )

}
