package sttp.openai.fixtures

object ChatFixture {

  val jsonRequest: String = """{
      |  "model": "gpt-3.5-turbo",
      |  "messages": [{"role": "user", "content": "Hello!"}],
      |  "temperature": 1,
      |  "top_p": 1,
      |  "n": 1,
      |  "stream": false,
      |  "stop": "\n",
      |  "max_tokens": 7,
      |  "presence_penalty": 0,
      |  "frequency_penalty": 0,
      |  "user": "testUser"
      |}""".stripMargin

  val jsonResponse: String = """{
      |   "id": "chatcmpl-76FxnKOjnPkDVYTAQ1wK8iUNFJPvR",
      |   "object": "chat.completion",
      |   "created": 1681725687,
      |   "model": "gpt-3.5-turbo-0301",
      |   "usage": {
      |     "prompt_tokens": 10,
      |     "completion_tokens": 10,
      |     "total_tokens": 20
      |   },
      |   "choices": [
      |     {
      |       "message": {
      |         "role": "assistance",
      |         "content": "Hi there! How can I assist you today?"
      |       },
      |       "finish_reason": "stop",
      |       "index": 0
      |     }
      |   ]
      |}""".stripMargin

}
