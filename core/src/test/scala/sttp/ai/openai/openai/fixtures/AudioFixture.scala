package sttp.openai.fixtures

object AudioFixture {
  val jsonResponse: String = """{
      | "text": "Imagine the wildest idea that you've ever had, and you're curious about how it might scale to something that's a 100, a 1,000 times bigger. This is a place where you can get to do that."
      |}""".stripMargin

  val jsonCreateSpeechRequest: String = """{
      | "model": "tts-1",
      | "input": "Hello, my name is John.",
      | "voice": "alloy",
      | "response_format": "mp3",
      | "speed": 1.0
      |}""".stripMargin
}
