package sttp.openai.fixtures

object CompletionsFixture {
  val jsonSinglePromptResponse: String = """{
                     |  "id": "cmpl-75C628xoevz3eE8zsTFDumZ5wqwmY",
                     |  "object": "text_completion",
                     |  "created": 1681472494,
                     |  "model": "gpt-3.5-turbo-instruct",
                     |  "choices": [
                     |    {
                     |      "text": "\n\nThis is indeed a test.",
                     |      "index": 0,
                     |      "logprobs": null,
                     |      "finish_reason": "stop"
                     |    }
                     |  ],
                     |  "usage": {
                     |    "prompt_tokens": 5,
                     |    "completion_tokens": 8,
                     |    "total_tokens": 13,
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
                     |  }
                     |}""".stripMargin

  /** Generated from: curl http://localhost:11434/v1/completions -d '{ "model": "llama3.2", "prompt": "Say Hello World as a haiku." }' */
  val ollamaPromptResponse: String = """{
                     |  "id": "cmpl-712",
                     |  "object": "text_completion",
                     |  "created": 1733664264,
                     |  "model": "llama3.2",
                     |  "system_fingerprint": "fp_ollama",
                     |  "choices": [
                     |    {
                     |      "text": "Greeting coding dawn\n\"Hello, world!\" echoes bright\nProgramming's start",
                     |      "index": 0,
                     |      "finish_reason": "stop"
                     |    }
                     |  ],
                     |  "usage": {
                     |    "prompt_tokens": 33,
                     |    "completion_tokens": 17,
                     |    "total_tokens": 50
                     |  }
                     |}""".stripMargin

  val jsonMultiplePromptResponse: String = """{
                    |  "id":"cmpl-76D8UlnqOEkhVXu29nY7UPZFDTTlP",
                    |  "object":"text_completion",
                    |  "created":1681714818,
                    |  "model":"gpt-3.5-turbo-instruct",
                    |  "choices":[
                    |    {
                    |      "text":"\n\nThis is indeed a test",
                    |      "index":0,
                    |      "logprobs":null,
                    |      "finish_reason":"length"
                    |    },
                    |    {
                    |      "text":"\n\nYes, this is also",
                    |      "index":1,
                    |      "logprobs":null,
                    |      "finish_reason":"length"
                    |    }
                    |  ],
                    |  "usage":{
                    |    "prompt_tokens":11,
                    |    "completion_tokens":14,
                    |    "total_tokens":25,
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
                    |  }
                    |}
                    |
                    |""".stripMargin

  val jsonSinglePromptRequest: String = """{
                              |  "model": "gpt-3.5-turbo-instruct",
                              |  "prompt": "Say this is a test",
                              |  "max_tokens": 7,
                              |  "temperature": 0,
                              |  "top_p": 1,
                              |  "n": 1,
                              |  "stop": "\n"
                              |}""".stripMargin

  val jsonMultiplePromptRequest: String = """{
                              |  "model": "gpt-3.5-turbo-instruct",
                              |  "prompt": ["Say this is a test", "Say this is also a test"],
                              |  "max_tokens": 7,
                              |  "temperature": 0,
                              |  "top_p": 1,
                              |  "n": 1,
                              |  "stop": "\n"
                              |}""".stripMargin
}
