package sttp.openai.fixtures

object ThreadsFixture {
  val jsonCreateEmptyThreadRequest: String = """{}
      |""".stripMargin

  val jsonCreateThreadWithMessagesRequestNoAttachments: String = """{
      |    "messages": [{
      |      "role": "user",
      |      "content": "Hello, what is AI?"
      |    }, {
      |      "role": "user",
      |      "content": "How does AI work? Explain it in simple terms."
      |    }]
      |  }""".stripMargin

  val jsonCreateThreadWithMessagesRequest: String = """{
    |    "messages": [{
    |      "role": "user",
    |      "content": "Hello, what is AI?",
    |      "attachments": [
    |        {
    |         "file_id" : "file-abc123",
    |         "tools": [
    |           { "type": "code_interpreter" },
    |           { "type": "file_search" }
    |         ]
    |        }
    |       ]
    |    }, {
    |      "role": "user",
    |      "content": "How does AI work? Explain it in simple terms."
    |    }]
    |  }""".stripMargin

  val jsonCreateThreadWithMessagesAndMetadataRequest: String = """{
    |    "messages": [{
    |      "role": "user",
    |      "content": "Hello, what is AI?",
    |      "attachments": [
    |        {
    |         "file_id" : "file-abc456",
    |         "tools": [
    |           { "type": "code_interpreter" }
    |         ]
    |        }
    |       ]
    |    }, {
    |      "role": "user",
    |      "content": "How does AI work? Explain it in simple terms."
    |    }],
    |    "metadata": {
    |      "modified": "true",
    |      "user": "abc123"
    |    }
    |}""".stripMargin

  val jsonCreateEmptyThreadResponse: String = """{
    |  "id": "thread_abc123",
    |  "object": "thread",
    |  "created_at": 1699012949,
    |  "metadata": {}
    |}""".stripMargin

  val jsonCreateThreadWithMessagesAndMetadataResponse: String = """{
    |  "id": "thread_abc123",
    |  "object": "thread",
    |  "created_at": 1699014083,
    |  "metadata": {
    |    "modified": "true",
    |    "user": "abc123"
    |  }
    |}""".stripMargin

  val jsonDeleteThreadResponse: String = """{
   |  "id": "thread_abc123",
   |  "object": "thread.deleted",
   |  "deleted": true
   |}
   |""".stripMargin

}
