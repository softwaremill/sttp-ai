package sttp.openai.requests.images

import sttp.openai.json.SnakePickle

object ImageResponseData {

  case class ImageResponse(
      created: Int,
      data: Seq[GeneratedImageData]
  )

  object ImageResponse {
    implicit val imageCreationResponseR: SnakePickle.Reader[ImageResponse] = SnakePickle.macroR[ImageResponse]
  }

  case class GeneratedImageData(url: String)

  object GeneratedImageData {
    implicit val generatedImageDataR: SnakePickle.Reader[GeneratedImageData] = SnakePickle.macroR[GeneratedImageData]
  }

}
