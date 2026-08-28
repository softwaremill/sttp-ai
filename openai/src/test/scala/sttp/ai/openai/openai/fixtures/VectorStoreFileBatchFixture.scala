package sttp.ai.openai.fixtures

object VectorStoreFileBatchFixture {

  val jsonCreateRequest: String =
    """{
      |  "file_ids": ["file_1", "file_2"]
      |}""".stripMargin

  val jsonObjectInProgress: String =
    """{
      |  "id": "vsfb_1",
      |  "object": "vector_store.file_batch",
      |  "created_at": 1698107661,
      |  "vector_store_id": "vs_1",
      |  "status": "in_progress",
      |  "file_counts": {
      |    "in_progress": 2,
      |    "completed": 0,
      |    "failed": 0,
      |    "cancelled": 0,
      |    "total": 2
      |  }
      |}""".stripMargin

  val jsonObjectCompleted: String =
    """{
      |  "id": "vsfb_1",
      |  "object": "vector_store.file_batch",
      |  "created_at": 1698107661,
      |  "vector_store_id": "vs_1",
      |  "status": "completed",
      |  "file_counts": {
      |    "in_progress": 0,
      |    "completed": 1,
      |    "failed": 1,
      |    "cancelled": 0,
      |    "total": 2
      |  }
      |}""".stripMargin

}
