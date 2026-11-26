package sttp.ai.openai.requests.images.edit

import sttp.ai.openai.requests.images.{ResponseFormat, Size}

import java.io.File
import java.nio.file.Paths

/** @param image
  *   The image(s) to edit. You can provide up to 16 images.
  * @param prompt
  *   A text description of the desired image(s). The maximum length is 32000 characters.
  * @param background
  *   Allows to set transparency for the background of the generated image(s). Must be one of `transparent`, `opaque` or `auto` (default
  *   value). If `transparent`, the output format must support transparency, so it should be set to either `png` (default value) or `webp`.
  * @param inputFidelity
  *   Control how much effort the model will exert to match the style and features, especially facial features, of input images. Supports
  *   `high` and `low`. Not supported by all models.
  * @param mask
  *   An additional image whose fully transparent areas (e.g. where alpha is zero) indicate where image should be edited. If there are
  *   multiple images provided, the mask will be applied on the first image. Must be a valid PNG file, less than 4MB, and have the same
  *   dimensions as image.
  * @param model
  *   The GPT image model to use for image editing.
  * @param n
  *   The number of images to generate. Must be between 1 and 10.
  * @param outputCompression
  *   The compression level (0-100%) for the generated images. Only applies to the `webp` or `jpeg` output formats, and defaults to 100.
  * @param outputFormat
  *   The format in which the generated images are returned, e.g. `png` (default value), `jpeg` or `webp`.
  * @param partialImages
  *   The number of partial images to generate. This parameter is used for streaming responses that return partial images. Value must be
  *   between 0 and 3. When set to 0, the response will be a single image sent in one streaming event. Note that the final image may be sent
  *   before the full number of partial images are generated if the full image is generated more quickly.
  * @param quality
  *   The quality of the image that will be generated, e.g. `low`, `medium`, `high` or `auto` (default value). Some models support
  *   additional values.
  * @param size
  *   The size of the generated images. Must be one of `1024x1024`, `1536x1024` (landscape), `1024x1536` (portrait), or `auto` (default
  *   value).
  * @param responseFormat
  *   Legacy parameter for retired image models. GPT image models always return base64-encoded images.
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

  case object GPTImage25Flare extends ImageEditsModel("gpt-image-2.5-flare")
  case object GPTImage25Flare20260908 extends ImageEditsModel("gpt-image-2.5-flare-2026-09-08")
  case object GPTImage25Sunburst extends ImageEditsModel("gpt-image-2.5-sunburst")
  case object GPTImage25Sunburst20260908 extends ImageEditsModel("gpt-image-2.5-sunburst-2026-09-08")
  case object GPTImage26Flare extends ImageEditsModel("gpt-image-2.6-flare")
  case object GPTImage26Sunburst extends ImageEditsModel("gpt-image-2.6-sunburst")

  case class CustomImageEditsModel(customImageEditsModel: String) extends ImageEditsModel(customImageEditsModel)
}
