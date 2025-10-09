package sttp.openai.fixtures

object ChatFixture {

  val jsonUpdateRequest: String =
    """{
      | "metadata": {
      |   "key": "value"
      | }
      |}""".stripMargin

  val jsonRequest: String =
    """{
      |  "messages": [
      |    {
      |      "role": "system",
      |      "content": "Hello!"
      |    },
      |    {
      |      "role": "system",
      |      "content": "Hello!",
      |      "name": "User"
      |    },
      |    {
      |      "role": "user",
      |      "content": "Hello!",
      |      "name": "User"
      |    },
      |    {
      |      "role": "user",
      |      "content": [
      |        {
      |          "type": "text",
      |          "text": "Hello!"
      |        },
      |        {
      |          "type": "image_url",
      |          "image_url": {
      |            "url": "https://i.imgur.com/2tj5rQE.jpg"
      |          }
      |        }
      |      ]
      |    },
      |    {
      |      "role": "assistant",
      |      "content": "Hello!",
      |      "name": "User",
      |      "tool_calls": [
      |        {
      |          "type": "function",
      |          "function": {
      |            "arguments": "args"
      |          }
      |        },
      |        {
      |          "type": "function",
      |          "function": {
      |            "arguments": "args",
      |            "name": "Fish"
      |          },
      |          "id": "tool_id_2"
      |        }
      |      ]
      |    },
      |    {
      |      "role": "assistant",
      |      "content": "Hello!",
      |      "name": "User"
      |    },
      |    {
      |      "role": "assistant",
      |      "content": "Hello!"
      |    },
      |    {
      |      "role": "tool",
      |      "content": "Hello!",
      |      "tool_call_id": "tool_call_id_1"
      |    },
      |    {
      |      "role": "tool",
      |      "content": "Hello!",
      |      "tool_call_id": "tool_call_id_2"
      |    }
      |  ],
      |  "model": "gpt-3.5-turbo",
      |  "frequency_penalty": 0,
      |  "max_tokens": 7,
      |  "n": 1,
      |  "presence_penalty": 0,
      |  "stop": "\n",
      |  "temperature": 1,
      |  "top_p": 1,
      |  "response_format": {
      |   "type": "json_object"
      |  },
      |  "tools": [
      |    {
      |      "type": "function",
      |      "function": {
      |        "description": "Random description",
      |        "name": "Random name",
      |        "parameters": {
      |          "type": "function",
      |          "properties": {
      |            "location": {
      |              "type": "string",
      |              "description": "The city and state e.g. San Francisco, CA"
      |            }
      |          },
      |          "required": ["location"]
      |        }
      |      }
      |    }
      |  ],
      |  "tool_choice": {
      |    "type": "function",
      |    "function": {
      |      "name": "function"
      |    }
      |  },
      |  "user": "testUser",
      |  "store": true,
      |  "reasoning_effort": "low",
      |  "metadata": {
      |    "key": "value"
      |  },
      |  "logprobs": true,
      |  "top_logprobs": 1,
      |  "max_completion_tokens": 10,
      |  "modalities": ["text", "audio"],
      |  "service_tier": "advanced",
      |  "parallel_tool_calls": true,
      |  "stream_options": {
      |    "include_usage": true
      |  },
      |  "prediction": {
      |    "type": "content",
      |    "content": [
      |      {
      |        "type": "code",
      |        "text": "simple text"
      |      }
      |    ]
      |  },
      |  "audio": {
      |    "voice": "ash",
      |    "format": "mp3"
      |  }
      |}""".stripMargin

  val jsonResponse: String =
    """{
      |  "id": "chatcmpl-76FxnKOjnPkDVYTAQ1wK8iUNFJPvR",
      |  "object": "chat.completion",
      |  "created": 1681725687,
      |  "model": "gpt-3.5-turbo-0301",
      |  "system_fingerprint": "systemFingerprint",
      |  "usage": {
      |    "prompt_tokens": 10,
      |    "completion_tokens": 10,
      |    "total_tokens": 20,
      |    "prompt_tokens_details": {
      |      "cached_tokens": 1,
      |      "audio_tokens": 2
      |    },
      |    "completion_tokens_details": {
      |      "reasoning_tokens": 4,
      |      "accepted_prediction_tokens": 3,
      |      "rejected_prediction_tokens": 2,
      |      "audio_tokens": 1
      |    }
      |  },
      |  "choices": [
      |    {
      |      "message": {
      |        "role": "assistant",
      |        "content": "Hi there! How can I assist you today?",
      |        "tool_calls": [
      |         {
      |           "type": "function",
      |           "function": {
      |             "arguments": "args"
      |           }
      |         },
      |         {
      |           "id": "tool_id_2",
      |           "type": "function",
      |           "function": {
      |             "arguments": "args",
      |             "name": "Fish"
      |           }
      |         }
      |        ]
      |      },
      |      "finish_reason": "stop",
      |      "index": 0,
      |      "logprobs": {
      |        "content": [
      |          {
      |            "token": "Hello",
      |            "logprob": -0.1,
      |            "bytes": [2, 3, 4],
      |            "top_logprobs": [
      |              {
      |                "token": "Hello",
      |                "logprob": -0.2,
      |                "bytes": [4, 5, 6]
      |              }
      |            ]
      |          }
      |        ],
      |        "refusal": [
      |          {
      |            "token": "Hello",
      |            "logprob": -0.1,
      |            "bytes": [2, 3, 4],
      |            "top_logprobs": [
      |              {
      |                "token": "Hello",
      |                "logprob": -0.2,
      |                "bytes": [4, 5, 6]
      |              }
      |            ]
      |          }
      |        ]
      |      }
      |    }
      |  ],
      |  "service_tier": "advanced"
      |}
      |""".stripMargin

  val jsonListMessageResponse: String =
    """{
      |  "object": "list",
      |  "data": [
      |    {
      |      "role": "assistant",
      |      "content": "Hi there! How can I assist you today?",
      |      "tool_calls": [],
      |      "id": "chatcmpl-76FxnKOjnPkDVYTAQ1wK8iUNFJPvR",
      |      "audio": {
      |        "id": "audio_id",
      |        "expires_at": 1681725687,
      |        "data": "base64encoded",
      |        "transcript": "transcript"
      |      }
      |    }
      |  ],
      |  "first_id": "chatcmpl-76FxnKOjnPkDVYTAQ1wK8iUNFJPvR",
      |  "last_id": "chatcmpl-76FxnKOjnPkDVYTAQ1wK8iUNFJPvR",
      |  "has_more": true
      |}
      |""".stripMargin

  val jsonListChatResponse: String =
    """{
      |  "object": "list",
      |  "data": [],
      |  "first_id": "chatcmpl-AyPNinnUqUDYo9SAdA52NobMflmj2",
      |  "last_id": "chatcmpl-AyPNinnUqUDYo9SAdA52NobMflmj2",
      |  "has_more": false
      |}
      |""".stripMargin

  val jsonDeleteChatCompletionResponse: String =
    """{
      |  "object": "chat.completion.deleted",
      |  "id": "chatcmpl-AyPNinnUqUDYo9SAdA52NobMflmj2",
      |  "deleted": true
      |}""".stripMargin

}
