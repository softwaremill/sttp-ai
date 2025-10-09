package sttp.openai.requests.images.edit

import sttp.openai.json.SnakePickle
import sttp.openai.requests.images.{ResponseFormat, Size}

import java.io.File
import java.nio.file.Paths

/** @param image
  *   The image(s) to edit. Must be a supported image file or an array of images. For `gpt-image-1`, each image should be a png, webp, or
  *   jpg file less than 50MB. You can provide up to 16 images. For `dall-e-2`, you can only provide one image, and it should be a square
  *   png file less than 4MB.
  * @param prompt
  *   A text description of the desired image(s). The maximum length is 1000 characters for `dall-e-2`, and 32000 characters for
  *   `gpt-image-1`.
  * @param background
  *   Allows to set transparency for the background of the generated image(s). This parameter is only supported for `gpt-image-1`. Must be
  *   one of `transparent`, `opaque` or `auto` (default value). When `auto` is used, the model will automatically determine the best
  *   background for the image. If `transparent`, the output format needs to support transparency, so it should be set to either `png`
  *   (default value) or `webp`.
  * @param inputFidelity
  *   Control how much effort the model will exert to match the style and features, especially facial features, of input images. This
  *   parameter is only supported for `gpt-image-1`. Supports `high` and `low`. Defaults to `low`.
  * @param mask
  *   An additional image whose fully transparent areas (e.g. where alpha is zero) indicate where image should be edited. If there are
  *   multiple images provided, the mask will be applied on the first image. Must be a valid PNG file, less than 4MB, and have the same
  *   dimensions as image.
  * @param model
  *   The model to use for image generation. Only `dall-e-2` and `gpt-image-1` are supported. Defaults to `dall-e-2` unless a parameter
  *   specific to `gpt-image-1` is used.
  * @param n
  *   The number of images to generate. Must be between 1 and 10.
  * @param outputCompression
  *   The compression level (0-100%) for the generated images. This parameter is only supported for `gpt-image-1` with the `webp` or `jpeg`
  *   output formats, and defaults to 100.
  * @param outputFormat
  *   The format in which the generated images are returned. This parameter is only supported for `gpt-image-1`. Must be one of `png`,
  *   `jpeg`, or `webp`. The default value is `png`.
  * @param partialImages
  *   The number of partial images to generate. This parameter is used for streaming responses that return partial images. Value must be
  *   between 0 and 3. When set to 0, the response will be a single image sent in one streaming event. Note that the final image may be sent
  *   before the full number of partial images are generated if the full image is generated more quickly.
  * @param quality
  *   The quality of the image that will be generated. `high`, `medium` and `low` are only supported for `gpt-image-1`. `dall-e-2` only
  *   supports `standard` quality. Defaults to `auto`.
  * @param size
  *   The size of the generated images. Must be one of `1024x1024`, `1536x1024` (landscape), `1024x1536` (portrait), or `auto` (default
  *   value) for `gpt-image-1`, one of `256x256`, `512x512`, or `1024x1024` for `dall-e-2`, and one of `1024x1024`, `1792x1024`, or
  *   `1024x1792` for `dall-e-3`.
  * @param responseFormat
  *   The format in which the generated images are returned. Must be one of `url` or `b64_json`. URLs are only valid for 60 minutes after
  *   the image has been generated. This parameter is only supported for `dall-e-2`, as `gpt-image-1` will always return base64-encoded
  *   images.
  * @param stream
  *   Edit the image in streaming mode. Defaults to `false`. See the Image generation guide for more information.
  * @param user
  *   A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse
  */
case class ImageEditsConfig(
    image: List[File],
    prompt: String,
    background: Option[String] = None,
    inputFidelity: Option[String] = None,
    mask: Option[File] = None,
    model: Option[ImageEditsModel] = None,
    n: Option[Int] = None,
    outputCompression: Option[Int] = None,
    outputFormat: Option[String] = None,
    partialImages: Option[Int] = None,
    quality: Option[String] = None,
    size: Option[Size] = None,
    responseFormat: Option[ResponseFormat] = None,
    stream: Option[Boolean] = None,
    user: Option[String] = None
)

object ImageEditsConfig {
  def createImageEditConfigWithSystemPaths(
      systemPathImages: List[String],
      prompt: String,
      background: Option[String] = None,
      inputFidelity: Option[String] = None,
      systemPathMask: Option[String] = None,
      model: Option[ImageEditsModel] = None,
      n: Option[Int] = None,
      outputCompression: Option[Int] = None,
      outputFormat: Option[String] = None,
      partialImages: Option[Int] = None,
      quality: Option[String] = None,
      size: Option[Size] = None,
      responseFormat: Option[ResponseFormat] = None,
      stream: Option[Boolean] = None,
      user: Option[String] = None
  ): ImageEditsConfig = {
    val images: List[File] = systemPathImages.map(Paths.get(_).toFile)
    val mask: Option[File] = systemPathMask.map(Paths.get(_).toFile)

    ImageEditsConfig(
      image = images,
      prompt = prompt,
      background = background,
      inputFidelity = inputFidelity,
      mask = mask,
      model = model,
      n = n,
      outputCompression = outputCompression,
      outputFormat = outputFormat,
      partialImages = partialImages,
      quality = quality,
      size = size,
      responseFormat = responseFormat,
      stream = stream,
      user = user
    )
  }
}

sealed abstract class ImageEditsModel(val value: String)

object ImageEditsModel {

  implicit val imageEditsModelW: SnakePickle.Writer[ImageEditsModel] = SnakePickle
    .writer[ujson.Value]
    .comap[ImageEditsModel](model => SnakePickle.writeJs(model.value))

  case object DALLE2 extends ImageEditsModel("dall-e-2")
  case object GPTImage1 extends ImageEditsModel("gpt-image-1")

  case class CustomImageEditsModel(customImageEditsModel: String) extends ImageEditsModel(customImageEditsModel)

}
