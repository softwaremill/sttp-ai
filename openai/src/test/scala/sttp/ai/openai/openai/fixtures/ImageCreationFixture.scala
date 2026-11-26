package sttp.ai.openai.fixtures

object ImageCreationFixture {
  val jsonRequest: String = """{
      |"prompt": "cute fish",
      |"background": "transparent",
      |"model": "gpt-image-2.6-sunburst",
      |"moderation": "strict",
      |"n": 1,
      |"output_compression": 80,
      |"output_format": "png",
      |"partial_images": 2,
      |"quality": "high",
      |"size": "1024x1024",
      |"response_format": "url",
      |"stream": false,
      |"style": "vivid",
      |"user": "user1"
      |}""".stripMargin

  val jsonRequestWithSomeOptionalsSetToNone: String = """{
      |"prompt": "cute fish",
      |"background": "transparent",
      |"model": "gpt-image-2.6-sunburst",
      |"n": 1,
      |"output_compression": 80,
      |"output_format": "png",
      |"quality": "high",
      |"size": "1024x1024",
      |"response_format": "url",
      |"stream": false,
      |"style": "vivid",
      |"user": "user1"
      |}""".stripMargin

  val jsonRequestDifferentModel: String = """{
      |"prompt": "cute fish",
      |"background": "transparent",
      |"model": "gpt-image-2.6-flare",
      |"moderation": "strict",
      |"n": 1,
      |"output_compression": 80,
      |"output_format": "png",
      |"partial_images": 2,
      |"quality": "high",
      |"size": "1024x1024",
      |"response_format": "url",
      |"stream": false,
      |"style": "vivid",
      |"user": "user1"
      |}""".stripMargin

  val jsonResponse: String = """{
      |"created": 1681893694,
      |"data": [
      |  {
      |    "url": "https://generated.image.url"
      |  }
      |]
      |}""".stripMargin
}
