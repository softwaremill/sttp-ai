package sttp.openai.fixtures

object AssistantsFixture {
  val jsonCreateAssistantRequest: String =
    """
      |{
      |  "instructions": "You are a personal math tutor. When asked a question, write and run Python code to answer the question.",
      |  "name": "Math Tutor",
      |  "tools": [{"type": "code_interpreter"}],
      |  "model": "gpt-4",
      |  "reasoning_effort": "low",
      |  "temperature": 1.0,
      |  "top_p": 1.0
      |}""".stripMargin

  val jsonCreateAssistantResponse: String =
    """
      |{
      |  "id": "asst_abc123",
      |  "object": "assistant",
      |  "created_at": 1698984975,
      |  "name": "Math Tutor",
      |  "description": null,
      |  "model": "gpt-4",
      |  "instructions": "You are a personal math tutor. When asked a question, write and run Python code to answer the question.",
      |  "tools": [
      |    {
      |      "type": "code_interpreter"
      |    }
      |  ],
      |  "tool_resources": {},
      |  "metadata": {}
      |}
      |""".stripMargin

  val jsonListAssistantsResponse: String =
    """
      |{
      |  "object": "list",
      |  "data": [
      |    {
      |      "id": "asst_abc123",
      |      "object": "assistant",
      |      "created_at": 1698982736,
      |      "name": "Coding Tutor",
      |      "description": null,
      |      "model": "gpt-4",
      |      "instructions": "You are a helpful assistant designed to make me better at coding!",
      |      "tools": [],
      |      "tool_resources": {},
      |      "metadata": {}
      |    },
      |    {
      |      "id": "asst_abc456",
      |      "object": "assistant",
      |      "created_at": 1698982718,
      |      "name": "My Assistant",
      |      "description": null,
      |      "model": "gpt-4",
      |      "instructions": "You are a helpful assistant designed to make me better at coding!",
      |      "tools": [],
      |      "tool_resources": {},
      |      "metadata": {}
      |    },
      |    {
      |      "id": "asst_abc789",
      |      "object": "assistant",
      |      "created_at": 1698982643,
      |      "name": null,
      |      "description": null,
      |      "model": "gpt-4",
      |      "instructions": null,
      |      "tools": [],
      |      "tool_resources": {},
      |      "metadata": {}
      |    }
      |  ],
      |  "first_id": "asst_abc123",
      |  "last_id": "asst_abc789",
      |  "has_more": false
      |}
      |""".stripMargin

  val jsonRetrieveAssistantResponse: String =
    """
      |{
      |  "id": "asst_abc123",
      |  "object": "assistant",
      |  "created_at": 1699009709,
      |  "name": "HR Helper",
      |  "description": null,
      |  "model": "gpt-4",
      |  "instructions": "You are an HR bot, and you have access to files to answer employee questions about company policies.",
      |  "tools": [
      |    {
      |      "type": "file_search"
      |    }
      |  ],
      |  "tool_resources": {
      |     "file_search": {
      |       "vector_store_ids": ["vs_1"]
      |     }
      |
      |  },
      |  "metadata": {}
      |}
      |""".stripMargin

  val jsonModifyAssistantRequest: String =
    """
      |{
      |  "instructions": "You are an HR bot, and you have access to files to answer employee questions about company policies. Always response with info from either of the files.",
      |  "tools": [{"type": "file_search"}],
      |  "model": "gpt-4",
      |  "tool_resources": {
      |     "file_search": {
      |        "vector_store_ids": ["vs_1", "vs_3"]
      |    }
      |  },
      |  "reasoning_effort": "low",
      |  "temperature": 1.0,
      |  "top_p": 1.0
      |}
      |""".stripMargin

  val jsonModifyAssistantResponse: String =
    """|
       |{
       |  "id": "asst_abc123",
       |  "object": "assistant",
       |  "created_at": 1699009709,
       |  "name": "HR Helper",
       |  "description": null,
       |  "model": "gpt-4",
       |  "instructions": "You are an HR bot, and you have access to files to answer employee questions about company policies. Always response with info from either of the files.",
       |  "tools": [
       |    {
       |      "type": "file_search"
       |    }
       |  ],
       |  "tool_resources": {
       |    "file_search": {
       |      "vector_store_ids": ["vs_1", "vs_2"]
       |    }
       |  },
       |  "metadata": {}
       |}
       |""".stripMargin

  val jsonDeleteAssistantResponse: String =
    """
      |{
      |  "id": "asst_abc123",
      |  "object": "assistant.deleted",
      |  "deleted": true
      |}
      |""".stripMargin
}
