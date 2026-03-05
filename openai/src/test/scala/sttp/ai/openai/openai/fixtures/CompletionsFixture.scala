package sttp.ai.openai.fixtures

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

  /** Response with partial completion_tokens_details - only some fields present This reproduces the issue from
    * https://github.com/softwaremill/sttp-ai/issues/432 where OpenAI returns only a subset of token detail fields
    */
  val jsonPartialTokenDetailsResponse: String = """{
                     |  "id": "cmpl-partial-test",
                     |  "object": "text_completion",
                     |  "created": 1681472500,
                     |  "model": "gpt-3.5-turbo-instruct",
                     |  "choices": [
                     |    {
                     |      "text": "\n\nTest response.",
                     |      "index": 0,
                     |      "logprobs": null,
                     |      "finish_reason": "stop"
                     |    }
                     |  ],
                     |  "usage": {
                     |    "prompt_tokens": 10,
                     |    "completion_tokens": 5,
                     |    "total_tokens": 15,
                     |    "completion_tokens_details": {
                     |      "reasoning_tokens": 0
                     |    }
                     |  }
                     |}""".stripMargin

  /**Structured Outputs/JSON Schema support
    * a response from Ollama with a structured content that can be deserialized to a typed value, in this case a JSON object with steps and final answer.
    */
  val structuredOutputsResponse =
    """{
      |  "id": "chatcmpl-689",
      |  "object": "chat.completion",
      |  "created": 1772595908,
      |  "model": "qwen3:8b",
      |  "system_fingerprint": "fp_ollama",
      |  "choices": [
      |    {
      |      "index": 0,
      |      "message": {
      |        "role": "assistant",
      |        "content": "{ \"steps\": [\n  {\n    \"explanation\": \"Start with the equation: 8x + 7 = -23.\",\n    \"output\": \"8x + 7 = -23\"\n  },\n  {\n    \"explanation\": \"Subtract 7 from both sides to isolate the term with x. This cancels out the +7 on the left and adjusts the right side accordingly.\",\n    \"output\": \"8x = -23 - 7\"\n  },\n  {\n    \"explanation\": \"Simplify the right side by performing the subtraction: -23 - 7 equals -30.\",\n    \"output\": \"8x = -30\"\n  },\n  {\n    \"explanation\": \"Divide both sides by 8 to solve for x. This undoes the multiplication by 8.\",\n    \"output\": \"x = -30 / 8\"\n  },\n  {\n    \"explanation\": \"Simplify the fraction by dividing both numerator and denominator by their greatest common divisor (which is 2).\",\n    \"output\": \"x = -15/4\"\n  },\n  {\n    \"explanation\": \"Verify the solution by substituting x = -15/4 back into the original equation to ensure both sides are equal.\",\n    \"output\": \"8(-15/4) + 7 = -30 + 7 = -23 ✓\"\n  }\n],\n\"finalAnswer\": \"x = -\\frac{15}{4}\" }",
      |        "reasoning": "Okay, so I need to solve the equation 8x + 7 = -23. Let me think about how to approach this. Hmm, algebra, right? The goal is to find the value of x that makes this equation true. Let me recall the steps for solving linear equations. \n\nFirst, I remember that to solve for x, I need to isolate it on one side of the equation. That means I have to get rid of the numbers around it. The equation has both a multiplication and an addition, so I should probably reverse those operations. The order of operations is important here. Since the equation has 8x plus 7, I need to undo the addition first and then the multiplication. \n\nWait, let me make sure. In the equation 8x + 7 = -23, the operations happening to x are first multiplying by 8 and then adding 7. To reverse that, I should do the opposite operations in the reverse order. So, first subtract 7 from both sides to undo the addition, and then divide by 8 to undo the multiplication. Yeah, that sounds right. \n\nLet me write that down step by step. Starting with the original equation:\n\n8x + 7 = -23\n\nFirst step: Subtract 7 from both sides to get rid of the +7 on the left. So:\n\n8x + 7 - 7 = -23 - 7\n\nSimplifying both sides:\n\nOn the left side, 7 - 7 cancels out, leaving 8x. On the right side, -23 - 7 is... let me calculate that. -23 minus 7 is like going further into the negatives. So that's -30. So now the equation is:\n\n8x = -30\n\nNow, the next step is to get x by itself. Since 8 is multiplied by x, I need to divide both sides by 8. \n\nSo:\n\n8x / 8 = -30 / 8\n\nSimplifying:\n\nx = -30/8\n\nHmm, can this fraction be simplified? Let me check. Both numerator and denominator are divisible by 2. Dividing numerator and denominator by 2:\n\n-30 ÷ 2 = -15\n\n8 ÷ 2 = 4\n\nSo, x = -15/4\n\nWait, is that the simplest form? Let me confirm. 15 and 4 have no common factors besides 1, so yes, -15/4 is the simplified fraction. Alternatively, as a mixed number, it would be -3 3/4, but unless the question specifies, the improper fraction is probably acceptable. \n\nLet me check my steps again to make sure I didn't make a mistake. Starting with 8x + 7 = -23. Subtract 7 from both sides: -23 -7 is indeed -30. Then divide by 8: -30 divided by 8. Yes, that's -3.75, which is equivalent to -15/4. \n\nAlternatively, maybe I can check by plugging the value back into the original equation to verify. Let's do that. If x = -15/4, then 8x is 8 * (-15/4). Let me compute that. 8 divided by 4 is 2, so 2 * (-15) = -30. Then add 7: -30 + 7 = -23. Which matches the right side of the equation. Perfect, that checks out. \n\nSo, the solution seems correct. Therefore, x equals -15 over 4. \n\nWait, but maybe I should present it as a decimal? The question didn't specify, but fractions are usually preferred in algebra unless told otherwise. So, -15/4 is the exact answer, while -3.75 is the decimal equivalent. \n\nAlternatively, if I wanted to write it as a mixed number, it's -3 and 3/4. But again, unless specified, improper fraction is fine. \n\nSo, to recap, the steps were:\n\n1. Subtract 7 from both sides: 8x = -30\n2. Divide both sides by 8: x = -30/8\n3. Simplify the fraction: x = -15/4\n\nYep, that's solid. I think that's the correct solution. No mistakes noticed in the process. The check confirms it. So, the answer should be x equals negative fifteen fourths.\n"
      |      },
      |      "finish_reason": "stop"
      |    }
      |  ],
      |  "usage": {
      |    "prompt_tokens": 1003,
      |    "completion_tokens": 317,
      |    "total_tokens": 1320
      |  }
      |}
      |""".stripMargin

  val structuredOutputsResponseWithoutChoices =
    """{
      |  "id": "chatcmpl-689",
      |  "object": "chat.completion",
      |  "created": 1772595908,
      |  "model": "qwen3:8b",
      |  "system_fingerprint": "fp_ollama",
      |  "choices": [],
      |  "usage": {
      |    "prompt_tokens": 1003,
      |    "completion_tokens": 317,
      |    "total_tokens": 1320
      |  }
      |}
      |""".stripMargin
}
