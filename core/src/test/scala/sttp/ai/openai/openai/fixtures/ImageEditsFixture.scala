package sttp.openai.fixtures

import sttp.openai.requests.images.edit.{ImageEditsConfig, ImageEditsModel}
import sttp.openai.requests.images.{ResponseFormat, Size}

import java.io.File
import java.nio.file.Files

trait ImageEditsFixture {
  def createTempImageFile(prefix: String, suffix: String = ".png"): File = {
    val tempFile = Files.createTempFile(prefix, suffix)
    // Create a minimal valid PNG file
    Files.write(
      tempFile,
      Array[Byte](0x89.toByte, 0x50.toByte, 0x4e.toByte, 0x47.toByte, 0x0d.toByte, 0x0a.toByte, 0x1a.toByte, 0x0a.toByte)
    )
    tempFile.toFile
  }

  val testImage1: File = createTempImageFile("test-image-1")
  val testImage2: File = createTempImageFile("test-image-2")
  val testMask: File = createTempImageFile("test-mask")

  val imageEditsConfigWithAllParametersSet: ImageEditsConfig = ImageEditsConfig(
    image = List(testImage1),
    prompt = "A test prompt",
    background = Some("transparent"),
    inputFidelity = Some("high"),
    mask = Some(testMask),
    model = Some(ImageEditsModel.GPTImage1),
    n = Some(2),
    outputCompression = Some(80),
    outputFormat = Some("png"),
    partialImages = Some(2),
    quality = Some("high"),
    size = Some(Size.Large),
    responseFormat = Some(ResponseFormat.URL),
    stream = Some(false),
    user = Some("test-user")
  )

  val multiImageEditsConfig: ImageEditsConfig = imageEditsConfigWithAllParametersSet.copy(
    image = List(testImage1, testImage2)
  )

  val minimalImageEditsConfig: ImageEditsConfig = ImageEditsConfig(
    image = List(testImage1),
    prompt = "A minimal test prompt"
  )
}
