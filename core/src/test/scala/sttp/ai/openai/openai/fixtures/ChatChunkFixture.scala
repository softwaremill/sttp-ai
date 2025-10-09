package sttp.openai.fixtures

object ChatChunkFixture {

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
      |          "id": "tool_id_2",
      |          "type": "function",
      |          "function": {
      |            "arguments": "args",
      |            "name": "Fish"
      |          }
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
      |   "type": "text"
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
      |  "tool_choice": "auto",
      |  "user": "testUser",
      |  "stream": true
      |}""".stripMargin

  val jsonResponse: String =
    """{
      |  "id": "chatcmpl-76FxnKOjnPkDVYTAQ1wK8iUNFJPvR",
      |  "object": "chat.completion",
      |  "created": 1681725687,
      |  "model": "gpt-3.5-turbo-0301",
      |  "system_fingerprint": "systemFingerprint",
      |  "choices": [
      |    {
      |      "delta": { 
      |        "content": "..." 
      |      },
      |      "index": 0
      |    },
      |    {
      |      "delta": {
      |        "role": "assistant",
      |        "content": "  Hi",
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
      |      "index": 1
      |    }
      |  ]
      |}""".stripMargin

}
