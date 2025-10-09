package sttp.openai.fixtures

object VectorStoreFileFixture {

  val jsonCreateRequest: String =
    """{
      |  "file_id": "file_1"
      |}""".stripMargin

  val jsonListRequest: String =
    """{
      |  "limit": 30,
      |  "order": "asc",
      |  "after": "111",
      |  "before": "222",
      |  "filter": "in_progress"
      |}""".stripMargin

  val jsonObject: String =
    """{
      |  "id": "vsf_1",
      |  "object": "vector_store.file",
      |  "usage_bytes": 123456,
      |  "created_at": 1698107661,
      |  "vector_store_id": "vs_1",
      |  "status": "completed",
      |  "last_error": null
      |}""".stripMargin

  val jsonObjectWithLastError: String =
    """{
      |  "id": "vsf_1",
      |  "object": "vector_store.file",
      |  "usage_bytes": 123456,
      |  "created_at": 1698107661,
      |  "vector_store_id": "vs_1",
      |  "status": "completed",
      |  "last_error": {
      |     "code": "server_error",
      |     "message": "Failed"
      |  }
      |}""".stripMargin

  val jsonList: String =
    """{
      |  "object": "list",
      |  "data": [
      |    {
      |      "id": "vsf_1",
      |      "object": "vector_store.file",
      |      "usage_bytes" : 123456,
      |      "status": "in_progress",
      |      "created_at": 1698107661,
      |      "vector_store_id": "vs_1"
      |    },
      |    {
      |      "id": "vsf_2",
      |      "object": "vector_store.file",
      |      "usage_bytes" : 1234567,
      |      "status": "completed",
      |      "created_at": 1698107661,
      |      "vector_store_id": "vs_1",
      |      "last_error": {
      |         "code": "rate_limit_exceeded",
      |         "message": "Failed2"
      |      }
      |    }
      |  ],
      |  "first_id": "vsf_1",
      |  "last_id": "vsf_2",
      |  "has_more": true
      |}""".stripMargin

  val jsonDelete: String =
    """{
      | "id": "file_abc123",
      | "object": "vector_store.file.deleted",
      | "deleted": true
      |}""".stripMargin
}
