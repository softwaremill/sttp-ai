//> using repository ivy2Local
//> using dep com.softwaremill.sttp.ai::claude:0.4.0
//> using dep ch.qos.logback:logback-classic:1.5.19

// remember to set the ANTHROPIC_API_KEY env variable!
// run with: ANTHROPIC_API_KEY=... scala-cli run ClaudeBasicExample.scala

package examples

import sttp.ai.claude.*
import sttp.ai.claude.ClaudeExceptions.ClaudeException
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ContentBlock, Message}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.MessageResponse
import sttp.client4.{DefaultSyncBackend, SyncBackend}

object ClaudeBasicExample extends App {

  // Configuration from environment
  val config = ClaudeConfig.fromEnv
  val backend: SyncBackend = DefaultSyncBackend()
  val client = ClaudeClient(config)

  // Basic text conversation
  println("=== Basic Text Conversation ===")

  val messages = List(
    Message.user("Hello Claude! What can you tell me about Scala programming?")
  )

  val request = MessageRequest.simple(
    model = "claude-3-haiku-20240307", // Using fastest model for example
    messages = messages,
    maxTokens = 500
  )

  val response = client.createMessage(request).send(backend)

  response.body match {
    case Right(messageResponse) =>
      println("Claude's response:")
      messageResponse.content.foreach {
        case ContentBlock.TextContent(text) => println(text)
        case _                              => // Handle other content types if needed
      }
      println(s"\nUsage: ${messageResponse.usage}")
    case Left(error) =>
      println(s"Error: ${error.getMessage}")
  }

  // Multi-turn conversation
  println("\n=== Multi-turn Conversation ===")

  val multiTurnMessages = List(
    Message.user("What is functional programming?"),
    Message.assistant(
      "Functional programming is a programming paradigm that treats computation as the evaluation of mathematical functions and avoids changing state and mutable data."
    ),
    Message.user("How does it relate to Scala?")
  )

  val multiTurnRequest = MessageRequest.simple(
    model = "claude-3-haiku-20240307",
    messages = multiTurnMessages,
    maxTokens = 300
  )

  val multiTurnResponse = client.createMessage(multiTurnRequest).send(backend)

  multiTurnResponse.body match {
    case Right(messageResponse) =>
      println("Claude's follow-up response:")
      messageResponse.content.foreach {
        case ContentBlock.TextContent(text) => println(text)
        case _                              => // Handle other content types if needed
      }
    case Left(error) =>
      println(s"Error: ${error.getMessage}")
  }

  // Using system message
  println("\n=== With System Message ===")

  val systemRequest = MessageRequest.withSystem(
    model = "claude-3-haiku-20240307",
    system = "You are a helpful assistant that always responds with exactly one sentence.",
    messages = List(Message.user("Explain quantum computing.")),
    maxTokens = 100
  )

  val systemResponse = client.createMessage(systemRequest).send(backend)

  systemResponse.body match {
    case Right(messageResponse) =>
      println("Claude's system-guided response:")
      messageResponse.content.foreach {
        case ContentBlock.TextContent(text) => println(text)
        case _                              => // Handle other content types if needed
      }
    case Left(error) =>
      println(s"Error: ${error.getMessage}")
  }

  // Advanced parameters
  println("\n=== With Advanced Parameters ===")

  val advancedRequest = MessageRequest(
    model = "claude-3-haiku-20240307",
    messages = List(Message.user("Write a creative short poem about programming.")),
    maxTokens = 200,
    temperature = Some(0.8), // More creative
    topP = Some(0.9),
    stopSequences = Some(List("---")) // Stop at triple dash
  )

  val advancedResponse = client.createMessage(advancedRequest).send(backend)

  advancedResponse.body match {
    case Right(messageResponse) =>
      println("Claude's creative response:")
      messageResponse.content.foreach {
        case ContentBlock.TextContent(text) => println(text)
        case _                              => // Handle other content types if needed
      }
    case Left(error) =>
      println(s"Error: ${error.getMessage}")
  }

  backend.close()
}
