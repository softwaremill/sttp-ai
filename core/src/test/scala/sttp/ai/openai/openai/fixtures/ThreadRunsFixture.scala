package sttp.openai.fixtures

object ThreadRunsFixture {

  val jsonCreateRunRequest: String =
    """
      |{
      |  "assistant_id": "asst_abc123"
      |}
      |""".stripMargin

  val jsonCreateRunResponse: String =
    """
      |{
      |  "id": "run_abc123",
      |  "object": "thread.run",
      |  "created_at": 1699063290,
      |  "assistant_id": "asst_abc123",
      |  "thread_id": "thread_abc123",
      |  "status": "queued",
      |  "started_at": 1699063290,
      |  "expires_at": null,
      |  "cancelled_at": null,
      |  "failed_at": null,
      |  "completed_at": 1699063291,
      |  "last_error": null,
      |  "model": "gpt-4",
      |  "instructions": null,
      |  "tools": [
      |    {
      |      "type": "code_interpreter"
      |    }
      |  ],
      |  "tool_resources": {
      |   "code_interpreter": {
      |     "file_ids": ["file-abc123", "file-abc456"]
      |     }
      |  },
      |  "metadata": {},
      |  "usage": null
      |}
      |""".stripMargin

  val jsonCreateThreadAndRunRequest: String =
    """
      |{
      |  "assistant_id": "asst_abc123",
      |  "thread": {
      |    "messages": [
      |      {"role": "user", "content": "Explain deep learning to a 5 year old."}
      |    ]
      |  }
      |}
      |""".stripMargin

  val jsonCreateThreadAndRunResponse: String =
    """
      |{
      |  "id": "run_abc123",
      |  "object": "thread.run",
      |  "created_at": 1699076792,
      |  "assistant_id": "asst_abc123",
      |  "thread_id": "thread_abc123",
      |  "status": "queued",
      |  "started_at": null,
      |  "expires_at": 1699077392,
      |  "cancelled_at": null,
      |  "failed_at": null,
      |  "completed_at": null,
      |  "last_error": null,
      |  "model": "gpt-4",
      |  "instructions": "You are a helpful assistant.",
      |  "tools": [],
      |  "tool_resources": {},
      |  "metadata": {},
      |  "usage": null
      |}
      |""".stripMargin

  val jsonListRunsResponse: String =
    """
      |{
      |  "object": "list",
      |  "data": [
      |    {
      |      "id": "run_abc123",
      |      "object": "thread.run",
      |      "created_at": 1699075072,
      |      "assistant_id": "asst_abc123",
      |      "thread_id": "thread_abc123",
      |      "status": "completed",
      |      "started_at": 1699075072,
      |      "expires_at": null,
      |      "cancelled_at": null,
      |      "failed_at": null,
      |      "completed_at": 1699075073,
      |      "last_error": null,
      |      "model": "gpt-3.5-turbo",
      |      "instructions": null,
      |      "tools": [
      |        {
      |          "type": "code_interpreter"
      |        }
      |      ],
      |      "tool_resources": {
      |       "code_interpreter": {
      |        "file_ids": ["file-abc123", "file-abc456"]
      |        }
      |      },
      |      "metadata": {},
      |      "usage": {
      |        "prompt_tokens": 123,
      |        "completion_tokens": 456,
      |        "total_tokens": 579
      |      }
      |    },
      |    {
      |      "id": "run_abc456",
      |      "object": "thread.run",
      |      "created_at": 1699063290,
      |      "assistant_id": "asst_abc123",
      |      "thread_id": "thread_abc123",
      |      "status": "completed",
      |      "started_at": 1699063290,
      |      "expires_at": null,
      |      "cancelled_at": null,
      |      "failed_at": null,
      |      "completed_at": 1699063291,
      |      "last_error": null,
      |      "model": "gpt-3.5-turbo",
      |      "instructions": null,
      |      "tools": [
      |        {
      |          "type": "code_interpreter"
      |        }
      |      ],
      |      "tool_resources": {
      |        "code_interpreter": {
      |         "file_ids": ["file-abc123", "file-abc456"]
      |         }
      |       },
      |      "metadata": {},
      |      "usage": {
      |        "prompt_tokens": 123,
      |        "completion_tokens": 456,
      |        "total_tokens": 579
      |      }
      |    }
      |  ],
      |  "first_id": "run_abc123",
      |  "last_id": "run_abc456",
      |  "has_more": false
      |}
      |""".stripMargin

  val jsonListRunStepsResponse: String =
    """
      |{
      |  "object": "list",
      |  "data": [
      |    {
      |      "id": "step_abc123",
      |      "object": "thread.run.step",
      |      "created_at": 1699063291,
      |      "run_id": "run_abc123",
      |      "assistant_id": "asst_abc123",
      |      "thread_id": "thread_abc123",
      |      "type": "message_creation",
      |      "status": "completed",
      |      "cancelled_at": null,
      |      "completed_at": 1699063291,
      |      "expired_at": null,
      |      "failed_at": null,
      |      "last_error": null,
      |      "step_details": {
      |        "type": "message_creation",
      |        "message_creation": {
      |          "message_id": "msg_abc123"
      |        }
      |      },
      |      "usage": {
      |        "prompt_tokens": 123,
      |        "completion_tokens": 456,
      |        "total_tokens": 579
      |      }
      |    }
      |  ],
      |  "first_id": "step_abc123",
      |  "last_id": "step_abc456",
      |  "has_more": false
      |}
      |""".stripMargin

  val jsonRetrieveRunResponse: String =
    """
      |{
      |  "id": "run_abc123",
      |  "object": "thread.run",
      |  "created_at": 1699075072,
      |  "assistant_id": "asst_abc123",
      |  "thread_id": "thread_abc123",
      |  "status": "completed",
      |  "started_at": 1699075072,
      |  "expires_at": null,
      |  "cancelled_at": null,
      |  "failed_at": null,
      |  "completed_at": 1699075073,
      |  "last_error": null,
      |  "model": "gpt-3.5-turbo",
      |  "instructions": null,
      |  "tools": [
      |    {
      |      "type": "code_interpreter"
      |    }
      |  ],
      |  "tool_resources": {
      |   "code_interpreter": {
      |     "file_ids": ["file-abc123", "file-abc456"]
      |     }
      |  },
      |  "metadata": {},
      |  "usage": {
      |    "prompt_tokens": 123,
      |    "completion_tokens": 456,
      |    "total_tokens": 579
      |  }
      |}
      |""".stripMargin

  val jsonRetrieveRunStepResponse: String =
    """
      |{
      |  "id": "step_abc123",
      |  "object": "thread.run.step",
      |  "created_at": 1699063291,
      |  "run_id": "run_abc123",
      |  "assistant_id": "asst_abc123",
      |  "thread_id": "thread_abc123",
      |  "type": "message_creation",
      |  "status": "completed",
      |  "cancelled_at": null,
      |  "completed_at": 1699063291,
      |  "expired_at": null,
      |  "failed_at": null,
      |  "last_error": null,
      |  "step_details": {
      |    "type": "message_creation",
      |    "message_creation": {
      |      "message_id": "msg_abc123"
      |    }
      |  },
      |  "usage": {
      |    "prompt_tokens": 123,
      |    "completion_tokens": 456,
      |    "total_tokens": 579
      |  }
      |}
      |""".stripMargin

  val jsonModifyRunRequest: String =
    """
      |{
      |  "metadata": {
      |     "user_id": "user_abc123"
      |   }
      |}
      |""".stripMargin

  val jsonModifyRunResponse: String =
    """
      |{
      |  "id": "run_abc123",
      |  "object": "thread.run",
      |  "created_at": 1699075072,
      |  "assistant_id": "asst_abc123",
      |  "thread_id": "thread_abc123",
      |  "status": "completed",
      |  "started_at": 1699075072,
      |  "expires_at": null,
      |  "cancelled_at": null,
      |  "failed_at": null,
      |  "completed_at": 1699075073,
      |  "last_error": null,
      |  "model": "gpt-3.5-turbo",
      |  "instructions": null,
      |  "tools": [
      |    {
      |      "type": "code_interpreter"
      |    }
      |  ],
      |  "tool_resources": {
      |   "code_interpreter": {
      |     "file_ids": ["file-abc123", "file-abc456"]
      |     }
      |  },
      |  "metadata": {
      |    "user_id": "user_abc123"
      |  },
      |  "usage": {
      |    "prompt_tokens": 123,
      |    "completion_tokens": 456,
      |    "total_tokens": 579
      |  }
      |}
      |""".stripMargin

  val jsonSubmitToolOutputsRequest: String =
    """
      |{
      |  "tool_outputs": [
      |    {
      |      "tool_call_id": "call_abc123",
      |      "output": "28C"
      |    }
      |  ]
      |}
      |""".stripMargin

  val jsonSubmitToolOutputsResponse: String =
    """
      |{
      |  "id": "run_abc123",
      |  "object": "thread.run",
      |  "created_at": 1699075592,
      |  "assistant_id": "asst_abc123",
      |  "thread_id": "thread_abc123",
      |  "status": "queued",
      |  "started_at": 1699075592,
      |  "expires_at": 1699076192,
      |  "cancelled_at": null,
      |  "failed_at": null,
      |  "completed_at": null,
      |  "last_error": null,
      |  "model": "gpt-4",
      |  "instructions": "You tell the weather.",
      |  "tools": [
      |    {
      |      "type": "function",
      |      "function": {
      |        "name": "get_weather",
      |        "description": "Determine weather in my location",
      |        "parameters": {
      |          "type": "object",
      |          "properties": {
      |            "location": {
      |              "type": "string",
      |              "description": "The city and state e.g. San Francisco, CA"
      |            },
      |            "unit": {
      |              "type": "string",
      |              "enum": [
      |                "c",
      |                "f"
      |              ]
      |            }
      |          },
      |          "required": [
      |            "location"
      |          ]
      |        }
      |      }
      |    }
      |  ],
      |  "tool_resources": {},
      |  "metadata": {},
      |  "usage": null
      |}
      |""".stripMargin

  val jsonCancelRunResponse: String =
    """
      |{
      |  "id": "run_abc123",
      |  "object": "thread.run",
      |  "created_at": 1699076126,
      |  "assistant_id": "asst_abc123",
      |  "thread_id": "thread_abc123",
      |  "status": "cancelling",
      |  "started_at": 1699076126,
      |  "expires_at": 1699076726,
      |  "cancelled_at": null,
      |  "failed_at": null,
      |  "completed_at": null,
      |  "last_error": null,
      |  "model": "gpt-4",
      |  "instructions": "You summarize books.",
      |  "tools": [
      |    {
      |      "type": "file_search"
      |    }
      |  ],
      |  "tool_resources": {},
      |  "metadata": {},
      |  "usage": null
      |}
      |""".stripMargin
}
