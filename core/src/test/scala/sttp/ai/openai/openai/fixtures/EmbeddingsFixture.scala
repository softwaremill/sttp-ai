package sttp.openai.fixtures

object EmbeddingsFixture {
  val jsonCreateEmbeddingsResponse = """{
                                       |  "object": "list",
                                       |  "data": [
                                       |    {
                                       |      "object": "embedding",
                                       |      "index": 0,
                                       |      "embedding": [
                                       |        0.0023064255,
                                       |        -0.009327292,
                                       |        0.015797347,
                                       |        -0.0077780345,
                                       |        -0.0046922187
                                       |        ]
                                       |    }
                                       |  ],
                                       |  "model": "text-embedding-ada-002",
                                       |  "usage": {
                                       |    "prompt_tokens": 8,
                                       |    "total_tokens": 8
                                       |  }
                                       |}""".stripMargin
}
