package sttp.ai.openai.requests.images

object ImageResponseData {

  case class ImageResponse(
      created: Int,
      data: Seq[GeneratedImageData]
  )

  case class GeneratedImageData(url: String)
}
