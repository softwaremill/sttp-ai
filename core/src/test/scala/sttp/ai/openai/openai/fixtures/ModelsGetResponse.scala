package sttp.openai.fixtures

object ModelsGetResponse {
  val responseJson: String =
    """{
      |  "object":"list",
      |  "data":[
      |    {
      |      "id":"babbage",
      |      "object":"model",
      |      "created":1649358449,
      |      "owned_by":"openai"
      |    },
      |    {
      |      "id":"davinci",
      |      "object":"model",
      |      "created":1649359874,
      |      "owned_by":"openai"
      |    }
      |  ]
      |}""".stripMargin

  val singleModelResponse = """{
                              |  "object":"list",
                              |  "data":[
                              |    {
                              |      "id":"babbage",
                              |      "object":"model",
                              |      "created":1649358449,
                              |      "owned_by":"openai"
                              |    }
                              |   ]
                              |}""".stripMargin
}
