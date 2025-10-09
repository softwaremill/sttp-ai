package sttp.openai.fixtures

object VectorStoreFixture {

  val jsonCreateRequest: String =
    """{
      |  "file_ids": ["file_1", "file_2"],
      |  "name": "vs_1"
      |}""".stripMargin

  val jsonCreateWithExpiresRequest: String =
    """{
      |  "file_ids": ["file_1", "file_2"],
      |  "name": "vs_1",
      |  "expires_after": {
      |   "anchor": "11111",
      |   "days": 2
      |   }
      |}""".stripMargin

  val jsonModify: String =
    """{
      |  "name": "vs_3",
      |  "expires_after": {
      |   "anchor": "2322",
      |   "days": 5
      |   }
      |}""".stripMargin

  val jsonObject: String =
    """{
      |  "id": "vs_1",
      |  "object": "vector_store",
      |  "created_at": 1698107661,
      |  "usage_bytes": 123456,
      |  "last_active_at": 1698107661,
      |  "name": "test_vs",
      |  "status": "in_progress",
      |  "expires_at": 1698107651,
      |  "file_counts": {
      |    "in_progress": 0,
      |    "completed": 1,
      |    "cancelled": 2,
      |    "failed": 1,
      |    "total": 4
      |  },
      |  "metadata": {},
      |  "last_used_at": 1698107681
      |}""".stripMargin

  val jsonList: String =
    """{
      |"object": "list",
      |  "data": [
      |    {
      |      "id": "vs_abc123",
      |      "object": "vector_store",
      |      "created_at": 1699061776,
      |      "name": "Support FAQ",
      |      "usage_bytes": 139920,
      |      "status": "completed",
      |      "file_counts": {
      |        "in_progress": 0,
      |        "completed": 3,
      |        "failed": 0,
      |        "cancelled": 0,
      |        "total": 3
      |      }
      |    },
      |    {
      |      "id": "vs_abc456",
      |      "object": "vector_store",
      |      "created_at": 1699061776,
      |      "name": "Support FAQ v2",
      |      "usage_bytes": 139921,
      |      "status": "in_progress",
      |      "file_counts": {
      |        "in_progress": 1,
      |        "completed": 2,
      |        "failed": 2,
      |        "cancelled": 1,
      |        "total": 6
      |      }
      |    }
      |  ],
      |  "first_id": "vs_abc123",
      |  "last_id": "vs_abc456",
      |  "has_more": false
      |}""".stripMargin

  val jsonDelete: String =
    """{
      | "id": "vs_abc123",
      | "object": "vector_store.deleted",
      | "deleted": true
      |}""".stripMargin
}
