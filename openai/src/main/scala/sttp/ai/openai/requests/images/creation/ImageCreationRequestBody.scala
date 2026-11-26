package sttp.ai.openai.requests.images.creation

import sttp.ai.openai.requests.images.{ResponseFormat, Size}

object ImageCreationRequestBody {

  /** @param prompt
    *   A text description of the desired image(s). The maximum length is 32000 characters.
    * @param background
    *   Allows to set transparency for the background of the generated image(s). Must be one of `transparent`, `opaque` or `auto` (default
    *   value). If `transparent`, the output format must support transparency, so it should be set to either `png` (default value) or
    *   `webp`.
    * @param model
    *   The GPT image model to use for image generation.
    * @param moderation
    *   Control the content-moderation level for generated images. Must be either `low` for less restrictive filtering or `auto` (default
    *   value).
    * @param n
    *   The number of images to generate. Must be between 1 and 10.
    * @param outputCompression
    *   The compression level (0-100%) for the generated images. Only applies to the `webp` or `jpeg` output formats, and defaults to 100.
    * @param outputFormat
    *   The format in which the generated images are returned, e.g. `png` (default value), `jpeg` or `webp`.
    * @param partialImages
    *   The number of partial images to generate. This parameter is used for streaming responses that return partial images. Value must be
    *   between 0 and 3. When set to 0, the response will be a single image sent in one streaming event. Note that the final image may be
    *   sent before the full number of partial images are generated if the full image is generated more quickly.
    * @param quality
    *   The quality of the image that will be generated, e.g. `low`, `medium`, `high` or `auto` (default value). Some models support
    *   additional values.
    * @param size
    *   The size of the generated images, e.g. `1024x1024`, `1536x1024` (landscape), `1024x1536` (portrait), or `auto` (default value). Some
    *   models accept other sizes.
    * @param responseFormat
    *   Legacy parameter for retired image models. GPT image models always return base64-encoded images.
    * @param stream
    *   Whether to stream the response. Must be one of `true` or `false`.
    * @param style
    *   Legacy parameter for retired image models. Describe the style in the prompt instead.
    * @param user
    *   A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
    */
  case class ImageCreationBody(
      prompt: String,
      background: Option[String] = None,
      model: ImageCreationModel,
      moderation: Option[String] = None,
      n: Option[Int] = None,
      outputCompression: Option[Int] = None,
      outputFormat: Option[String] = None,
      partialImages: Option[Int] = None,
      quality: Option[String] = None,
      size: Option[Size] = None,
      responseFormat: Option[ResponseFormat] = None,
      stream: Option[Boolean] = None,
      style: Option[String] = None,
      user: Option[String] = None
  )

  sealed abstract class ImageCreationModel(val value: String)

  object ImageCreationModel {

    case object GPTImage25Flare extends ImageCreationModel("gpt-image-2.5-flare")
    case object GPTImage25Flare20260908 extends ImageCreationModel("gpt-image-2.5-flare-2026-09-08")
    case object GPTImage25Sunburst extends ImageCreationModel("gpt-image-2.5-sunburst")
    case object GPTImage25Sunburst20260908 extends ImageCreationModel("gpt-image-2.5-sunburst-2026-09-08")
    case object GPTImage26Flare extends ImageCreationModel("gpt-image-2.6-flare")
    case object GPTImage26Sunburst extends ImageCreationModel("gpt-image-2.6-sunburst")
    case class CustomImageCreationModel(customImageCreationModel: String) extends ImageCreationModel(customImageCreationModel)
  }

}
