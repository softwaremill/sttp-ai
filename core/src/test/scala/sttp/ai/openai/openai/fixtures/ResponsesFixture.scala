package sttp.openai.fixtures

object ResponsesFixture {

  val jsonGetResponseQueryParameters: String =
    """{
      |  "include": ["code_interpreter_call.outputs", "message.output_text.logprobs"],
      |  "include_obfuscation": false,
      |  "starting_after": 42,
      |  "stream": true
      |}""".stripMargin

  val jsonRequest: String =
    """{
      |  "background": false,
      |  "include": ["code_interpreter_call.outputs", "message.output_text.logprobs"],
      |  "input": "What is the capital of France?",
      |  "instructions": "You are a helpful assistant",
      |  "max_output_tokens": 1000,
      |  "max_tool_calls": 5,
      |  "metadata": {
      |    "key1": "value1",
      |    "key2": "value2"
      |  },
      |  "model": "gpt-4o",
      |  "parallel_tool_calls": true,
      |  "previous_response_id": "prev_resp_123",
      |  "prompt": {
      |    "id": "prompt_123",
      |    "variables": {
      |      "var1": "val1"
      |    },
      |    "version": "1.0"
      |  },
      |  "prompt_cache_key": "cache_key_123",
      |  "reasoning": {
      |    "effort": "high",
      |    "summary": "detailed"
      |  },
      |  "safety_identifier": "safety_123",
      |  "service_tier": "auto",
      |  "store": true,
      |  "stream": false,
      |  "temperature": 0.7,
      |  "text": {
      |    "format": {
      |      "schema": {"type": "string"},
      |      "name": "response_schema",
      |      "description": "Response format",
      |      "strict": true,
      |      "type": "json_schema"
      |    }
      |  },
      |  "tool_choice": "auto",
      |  "tools": [{"type":"mcp","server_label":"label","server_url":"url"}],
      |  "top_logprobs": 5,
      |  "top_p": 0.9,
      |  "truncation": "disabled",
      |  "user": "user123"
      |}""".stripMargin

  val jsonRequestWithInputMessage: String =
    """{
      |  "input": [{
      |    "type": "message",
      |    "content": [
      |      {
      |        "type": "input_text",
      |        "text": "what is in this image?"
      |      },
      |      {
      |        "type": "input_image",
      |        "file_id": null,
      |        "detail": "auto",
      |        "image_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Gfp-wisconsin-madison-the-nature-boardwalk.jpg/2560px-Gfp-wisconsin-madison-the-nature-boardwalk.jpg"
      |      }
      |    ],
      |    "role":"user",
      |    "status":null
      |  }],
      |  "model": "gpt-4.1"
      |}""".stripMargin

  val jsonRequestWithInputFile: String =
    """{
      |  "input": [
      |    {
      |      "type": "message",
      |      "content": [
      |        {"type": "input_text", "text": "what is in this file?"},
      |        {
      |          "type": "input_file",
      |          "file_data":null,
      |          "file_id":null,
      |          "file_url": "https://www.berkshirehathaway.com/letters/2024ltr.pdf",
      |          "filename": null
      |        }
      |      ],
      |      "role": "user",
      |      "status":null
      |    }
      |  ],
      |  "model": "gpt-4.1"
      |}""".stripMargin

  val jsonRequestWithFileSearchToolCall: String =
    """{
      |  "model": "gpt-4.1",
      |  "input": [
      |    {
      |      "type": "file_search_call",
      |      "id": "call_abc123",
      |      "queries": ["machine learning algorithms", "neural networks"],
      |      "status": "completed",
      |      "results": [
      |        {
      |          "file_id": "file-abc123",
      |          "filename": "ml_algorithms.pdf",
      |          "text": "Neural networks are a subset of machine learning..."
      |        }
      |      ]
      |    }
      |  ]
      |}""".stripMargin

  val jsonRequestWithFileSearchToolCallInProgress: String =
    """{
      |  "model": "gpt-4.1",
      |  "input": [
      |    {
      |      "type": "file_search_call",
      |      "id": "call_def456",
      |      "queries": ["python programming", "data analysis"],
      |      "status": "in_progress"
      |    }
      |  ]
      |}""".stripMargin

  val jsonResponseBasic: String =
    """{
      |  "id": "resp_67ccd3a9da748190baa7f1570fe91ac604becb25c45c1d41",
      |  "object": "response",
      |  "created_at": 1741476777,
      |  "status": "completed",
      |  "error": null,
      |  "incomplete_details": null,
      |  "instructions": null,
      |  "max_output_tokens": null,
      |  "model": "gpt-4o-2024-08-06",
      |  "output": [
      |    {
      |      "type": "message",
      |      "id": "msg_67ccd3acc8d48190a77525dc6de64b4104becb25c45c1d41",
      |      "status": "completed",
      |      "role": "assistant",
      |      "content": [
      |        {
      |          "type": "output_text",
      |          "text": "The image depicts a scenic landscape with a wooden boardwalk or pathway leading through lush, green grass under a blue sky with some clouds. The setting suggests a peaceful natural area, possibly a park or nature reserve. There are trees and shrubs in the background.",
      |          "annotations": []
      |        }
      |      ]
      |    }
      |  ],
      |  "parallel_tool_calls": true,
      |  "previous_response_id": null,
      |  "reasoning": {
      |    "effort": null,
      |    "summary": null
      |  },
      |  "store": true,
      |  "temperature": 1,
      |  "text": {
      |    "format": {
      |      "type": "text"
      |    }
      |  },
      |  "tool_choice": "auto",
      |  "tools": [],
      |  "top_p": 1,
      |  "truncation": "disabled",
      |  "usage": {
      |    "input_tokens": 328,
      |    "input_tokens_details": {
      |      "cached_tokens": 0
      |    },
      |    "output_tokens": 52,
      |    "output_tokens_details": {
      |      "reasoning_tokens": 0
      |    },
      |    "total_tokens": 380
      |  },
      |  "user": null,
      |  "metadata": {}
      |}""".stripMargin

  val jsonResponseWithComplexOutput: String =
    """{
      |  "id": "resp_complex123",
      |  "object": "response",
      |  "created_at": 1741476778,
      |  "status": "completed",
      |  "error": null,
      |  "incomplete_details": null,
      |  "instructions": "You are a helpful assistant",
      |  "max_output_tokens": 2000,
      |  "model": "gpt-4o",
      |  "output": [
      |    {
      |      "type": "message",
      |      "id": "msg_complex123",
      |      "status": "completed",
      |      "role": "assistant",
      |      "content": [
      |        {
      |          "type": "output_text",
      |          "text": "I'll search for information about machine learning.",
      |          "annotations": [
      |            {
      |              "type": "file_citation",
      |              "file_id": "file-123",
      |              "filename": "ml_guide.pdf",
      |              "index": 0
      |            }
      |          ]
      |        }
      |      ]
      |    },
      |    {
      |      "type": "file_search_call",
      |      "id": "call_search123",
      |      "queries": ["machine learning", "neural networks"],
      |      "status": "completed",
      |      "results": [
      |        {
      |          "file_id": "file-123",
      |          "filename": "ml_guide.pdf",
      |          "score": 0.95,
      |          "text": "Machine learning is a subset of artificial intelligence...",
      |          "attributes": {
      |            "page": 1,
      |            "section": "introduction"
      |          }
      |        }
      |      ]
      |    },
      |    {
      |      "type": "code_interpreter_call",
      |      "id": "code_call123",
      |      "container_id": "container_123",
      |      "code": "import numpy as np\nprint('Hello ML')",
      |      "status": "completed",
      |      "outputs": [
      |        {
      |          "type": "logs",
      |          "logs": "Hello ML\n"
      |        }
      |      ]
      |    }
      |  ],
      |  "parallel_tool_calls": false,
      |  "previous_response_id": "prev_resp_123",
      |  "reasoning": {
      |    "effort": "medium",
      |    "summary": "concise"
      |  },
      |  "store": false,
      |  "temperature": 0.7,
      |  "text": {
      |    "format": {
      |      "type": "json_schema",
      |      "name": "ml_response",
      |      "schema": {"type": "object"},
      |      "description": "ML response format",
      |      "strict": true
      |    }
      |  },
      |  "tool_choice": "auto",
      |  "tools": [],
      |  "top_p": 0.9,
      |  "truncation": "auto",
      |  "usage": {
      |    "input_tokens": 500,
      |    "input_tokens_details": {
      |      "cached_tokens": 100
      |    },
      |    "output_tokens": 150,
      |    "output_tokens_details": {
      |      "reasoning_tokens": 50
      |    },
      |    "total_tokens": 650
      |  },
      |  "user": "user123",
      |  "metadata": {
      |    "session_id": "session_123",
      |    "experiment": "test_run"
      |  }
      |}""".stripMargin

  val jsonResponseWithAllowedToolsChoice: String =
    """{
      |  "id": "resp_tool_choice_123",
      |  "object": "response",
      |  "created_at": 1741476779,
      |  "model": "gpt-4o",
      |  "status": "completed",
      |  "tool_choice": {
      |    "type": "allowed_tools",
      |    "mode": "auto",
      |    "tools": [
      |      {
      |        "type": "function",
      |        "name": "get_weather"
      |      },
      |      {
      |        "type": "mcp",
      |        "server_label": "deepwiki"
      |      },
      |      {
      |        "type": "image_generation"
      |      }
      |    ]
      |  },
      |  "output": []
      |}""".stripMargin
}
