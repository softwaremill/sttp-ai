package sttp.openai.requests.images.variations

import sttp.openai.requests.images.{ResponseFormat, Size}

import java.io.File
import java.nio.file.Paths

/** @param image
  *   The image to use as the basis for the variation(s). Must be a valid PNG file, less than 4MB, and square.
  * @param n
  *   The number of images to generate. Must be between 1 and 10.
  * @param size
  *   The size of the generated images. Must be one of `256x256`, `512x512`, or `1024x1024`.
  * @param responseFormat
  *   The format in which the generated images are returned. Must be one of `url` or `b64_json`.
  * @param user
  *   A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
  */
case class ImageVariationsConfig(
    image: File,
    n: Option[Int] = None,
    size: Option[Size] = None,
    responseFormat: Option[ResponseFormat] = None,
    user: Option[String] = None
)

object ImageVariationsConfig {
  def createImageVariationConfigWithSystemPaths(
      systemPathImage: String,
      n: Option[Int],
      size: Option[Size],
      responseFormat: Option[ResponseFormat],
      user: Option[String]
  ): ImageVariationsConfig =
    ImageVariationsConfig(
      Paths.get(systemPathImage).toFile,
      n,
      size,
      responseFormat,
      user
    )
}
