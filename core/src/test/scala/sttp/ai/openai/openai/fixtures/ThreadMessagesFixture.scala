package sttp.openai.fixtures

object ThreadMessagesFixture {

  val jsonCreateMessageRequest: String =
    """{
      |  "role": "user",
      |  "content": "How does AI work? Explain it in simple terms."
      |}""".stripMargin

  val jsonCreateMessageResponse: String =
    """
      |{
      |  "id": "msg_abc123",
      |  "object": "thread.message",
      |  "created_at": 1699017614 ,
      |  "thread_id": "thread_abc123",
      |  "role": "user",
      |  "content": [
      |    {
      |      "type": "text",
      |      "text": {
      |        "value": "How does AI work? Explain it in simple terms.",
      |        "annotations": []
      |      }
      |    }
      |  ],
      |  "attachments": [],
      |  "assistant_id": null,
      |  "run_id": null,
      |  "metadata": {}
      |}
      |""".stripMargin

  val jsonListMessagesResponse: String =
    """
      |{
      |  "object": "list",
      |  "data": [
      |    {
      |      "id": "msg_abc123",
      |      "object": "thread.message",
      |      "created_at": 1699016383,
      |      "thread_id": "thread_abc123",
      |      "role": "user",
      |      "content": [
      |        {
      |          "type": "text",
      |          "text": {
      |            "value": "How does AI work? Explain it in simple terms.",
      |            "annotations": []
      |          }
      |        }
      |      ],
      |      "attachments": [],
      |      "assistant_id": null,
      |      "run_id": null,
      |      "metadata": {}
      |    },
      |    {
      |      "id": "msg_abc456",
      |      "object": "thread.message",
      |      "created_at": 1699016383,
      |      "thread_id": "thread_abc123",
      |      "role": "user",
      |      "content": [
      |        {
      |          "type": "text",
      |          "text": {
      |            "value": "Hello, what is AI?",
      |            "annotations": []
      |          }
      |        }
      |      ],
      |      "attachments": [
      |        {
      |         "file_id" : "file-abc123",
      |         "tools": []
      |        }
      |       ],
      |      "assistant_id": null,
      |      "run_id": null,
      |      "metadata": {}
      |    }
      |  ],
      |  "first_id": "msg_abc123",
      |  "last_id": "msg_abc456",
      |  "has_more": false
      |}
      |""".stripMargin

  val jsonRetrieveMessageResponse: String =
    """
      |{
      |  "id": "msg_abc123",
      |  "object": "thread.message",
      |  "created_at": 1699017614,
      |  "thread_id": "thread_abc123",
      |  "role": "user",
      |  "content": [
      |    {
      |      "type": "text",
      |      "text": {
      |        "value": "How does AI work? Explain it in simple terms.",
      |        "annotations": []
      |      }
      |    }
      |  ],
      |  "assistant_id": null,
      |  "run_id": null,
      |  "metadata": {}
      |}
      |""".stripMargin

  val jsonModifyMessageResponse: String =
    """
      |{
      |  "id": "msg_abc123",
      |  "object": "thread.message",
      |  "created_at": 1699017614,
      |  "thread_id": "thread_abc123",
      |  "role": "user",
      |  "content": [
      |    {
      |      "type": "text",
      |      "text": {
      |        "value": "How does AI work? Explain it in simple terms.",
      |        "annotations": []
      |      }
      |    }
      |  ],
      |  "attachments": [],
      |  "assistant_id": null,
      |  "run_id": null,
      |  "metadata": {
      |    "modified": "true",
      |    "user": "abc123"
      |  }
      |}
      |""".stripMargin

  val jsonDeleteMessageResponse: String =
    """{
       |  "id": "msg_abc123",
       |  "object": "thread.message.deleted",
       |  "deleted": true
       |}""".stripMargin
}
