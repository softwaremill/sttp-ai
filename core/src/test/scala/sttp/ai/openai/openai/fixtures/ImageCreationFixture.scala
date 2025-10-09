package sttp.openai.fixtures

object ImageCreationFixture {
  val jsonRequest: String = """{
      |"prompt": "cute fish",
      |"background": "transparent",
      |"model": "dall-e-3",
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
      |"model": "dall-e-3",
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

  val jsonRequestDalle2: String = """{
      |"prompt": "cute fish",
      |"background": "transparent",
      |"model": "dall-e-2",
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
