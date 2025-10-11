//> using repository ivy2Local
//> using dep com.softwaremill.sttp.ai::claude:0.4.0
//> using dep ch.qos.logback:logback-classic:1.5.19

// remember to set the ANTHROPIC_API_KEY env variable!
// run with: ANTHROPIC_API_KEY=... scala-cli run ClaudeImageAnalysisExample.scala

package examples

import sttp.ai.claude.*
import sttp.ai.claude.ClaudeExceptions.ClaudeException
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, Message}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.MessageResponse
import sttp.client4.{DefaultSyncBackend, SyncBackend}

import java.awt.image.BufferedImage
import java.awt.{Color, Graphics2D}
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

object ClaudeImageAnalysisExample extends App {

  val config = ClaudeConfig.fromEnv
  val backend: SyncBackend = DefaultSyncBackend()
  val client = ClaudeClient(config)

  println("=== Claude Image Analysis Example ===")

  // Create a simple test image programmatically (since we can't rely on external files)
  val testImageBase64 = createTestImage()

  // Single image analysis
  val messages = List(
    Message.user(
      List(
        ContentBlock.TextContent("What do you see in this image? Please describe it in detail."),
        ContentBlock.ImageContent(
          source = ContentBlock.ImageSource.base64("image/png", testImageBase64)
        )
      )
    )
  )

  val request = MessageRequest.simple(
    model = "claude-3-haiku-20240307", // Use a vision-capable model
    messages = messages,
    maxTokens = 500
  )

  val response = client.createMessage(request).send(backend)

  response.body match {
    case Right(messageResponse) =>
      println("Claude's image analysis:")
      messageResponse.content.foreach {
        case ContentBlock.TextContent(text) => println(text)
        case _                              => // Handle other content types if needed
      }
      println(s"\nUsage: ${messageResponse.usage}")
    case Left(error) =>
      println(s"Error: ${error.getMessage}")
  }

  // Multiple images with comparison
  println("\n=== Multiple Image Comparison ===")

  val testImage2Base64 = createTestImage(Color.BLUE)

  val multiImageMessages = List(
    Message.user(
      List(
        ContentBlock.TextContent("Compare these two images and tell me the differences:"),
        ContentBlock.TextContent("First image:"),
        ContentBlock.ImageContent(
          source = ContentBlock.ImageSource.base64("image/png", testImageBase64)
        ),
        ContentBlock.TextContent("Second image:"),
        ContentBlock.ImageContent(
          source = ContentBlock.ImageSource.base64("image/png", testImage2Base64)
        )
      )
    )
  )

  val multiImageRequest = MessageRequest.simple(
    model = "claude-3-haiku-20240307",
    messages = multiImageMessages,
    maxTokens = 600
  )

  val multiImageResponse = client.createMessage(multiImageRequest).send(backend)

  multiImageResponse.body match {
    case Right(messageResponse) =>
      println("Claude's comparison:")
      messageResponse.content.foreach {
        case ContentBlock.TextContent(text) => println(text)
        case _                              => // Handle other content types if needed
      }
    case Left(error) =>
      println(s"Error: ${error.getMessage}")
  }

  // Image with specific analysis request
  println("\n=== Specific Image Analysis Task ===")

  val specificMessages = List(
    Message.user(
      List(
        ContentBlock.TextContent("Analyze this image for any geometric shapes and their properties (color, size, position):"),
        ContentBlock.ImageContent(
          source = ContentBlock.ImageSource.base64("image/png", testImageBase64)
        )
      )
    )
  )

  val specificRequest = MessageRequest.withSystem(
    model = "claude-3-haiku-20240307",
    system = "You are an expert in image analysis. Provide detailed technical descriptions of visual elements.",
    messages = specificMessages,
    maxTokens = 400
  )

  val specificResponse = client.createMessage(specificRequest).send(backend)

  specificResponse.body match {
    case Right(messageResponse) =>
      println("Claude's technical analysis:")
      messageResponse.content.foreach {
        case ContentBlock.TextContent(text) => println(text)
        case _                              => // Handle other content types if needed
      }
    case Left(error) =>
      println(s"Error: ${error.getMessage}")
  }

  backend.close()

  // Helper method to create a test image programmatically
  private def createTestImage(color: Color = Color.RED): String = {
    val width = 200
    val height = 200

    val bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics: Graphics2D = bufferedImage.createGraphics()

    // Fill background with white
    graphics.setColor(Color.WHITE)
    graphics.fillRect(0, 0, width, height)

    // Draw a colored circle
    graphics.setColor(color)
    graphics.fillOval(50, 50, 100, 100)

    // Draw a rectangle
    graphics.setColor(Color.GREEN)
    graphics.fillRect(20, 150, 60, 30)

    // Draw some text
    graphics.setColor(Color.BLACK)
    graphics.drawString("Test Image", 10, 20)

    graphics.dispose()

    // Convert to base64
    val baos = new ByteArrayOutputStream()
    ImageIO.write(bufferedImage, "png", baos)
    val imageBytes = baos.toByteArray
    baos.close()

    Base64.getEncoder.encodeToString(imageBytes)
  }
}
