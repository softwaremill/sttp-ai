package examples

import sttp.ai.openai.config.OpenAIConfig
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel, ResponseFormat}
import sttp.ai.openai.requests.completions.chat.message.{Content, Message, Tool}
import sttp.ai.openai.{OpenAI, OpenAIZioClient}
import sttp.client4.httpclient.zio.{HttpClientZioBackend, SttpClient}
import sttp.client4.logging.LogConfig
import sttp.client4.logging.slf4j.Slf4jLoggingBackend
import sttp.client4.*
import sttp.tapir.Schema as TapirSchema
import sttp.tapir.generic.auto.*

import zio.*
import zio.json.*

case class Step(explanation: String, output: String) derives JsonDecoder

case class MathReasoning(steps: List[Step], finalAnswer: String) derives JsonDecoder, TapirSchema

class OpenAIZioClientExample private (client: OpenAIZioClient) {
  def hello(): Task[Unit] = for {
    response <- client.createChatCompletion(chatBody(Seq(userMessage("Hello!"))))
    _ <- Console.printLine(s"Response: $response")
  } yield ()

  def streaming(): Task[Unit] = for {
    finalRes <- client.createStreamedChatCompletion(chatBody(Seq(userMessage("Hello!"))))
    _ <- finalRes
      .map(_.choices.head.delta.content.getOrElse(""))
      .filter(_.nonEmpty)
      .foreach(Console.printLine(_))
  } yield ()

  val bodyMessages: Seq[Message] = Seq(
    systemMessage(
      "You are a helpful math tutor. Guide the user through the solution step by step."
    ),
    userMessage("How can I solve 8x + 7 = -23")
  )

  def structuredOutputFormat() = for {
    client <- ZIO.service[OpenAIZioClient]
    response <- client.createChatCompletion[MathReasoning](chatBody(bodyMessages)) {
      _.fromJson[MathReasoning]
    }
    _ <- Console.printLine(s"Response: $response")
  } yield ()

  // util methods
  val model: ChatCompletionModel.CustomChatCompletionModel =
    ChatCompletionModel.CustomChatCompletionModel("qwen3:8b")

  def userMessage(content: String): Message =
    Message.UserMessage(
      content = Content.TextContent(content)
    )

  def systemMessage(content: String): Message =
    Message.SystemMessage(
      content = content
    )

  def chatBody(
      messages: Seq[Message],
      responseFormat: Option[ResponseFormat] = None,
      tools: Option[Seq[Tool]] = None
  ) =
    ChatBody(
      model = model,
      messages = messages,
      responseFormat = responseFormat,
      tools = tools
    )
}

object OpenAIZioClientExample extends ZIOAppDefault {
  val live: ZLayer[OpenAIZioClient, Any, OpenAIZioClientExample] =
    ZLayer.fromFunction((client: OpenAIZioClient) => new OpenAIZioClientExample(client))

  val program = for {
    service <- ZIO.service[OpenAIZioClientExample]
    _ <- Console.printLine(s"hello example") *> service.hello()
    _ <- Console.printLine(s"streaming example") *> service.streaming()
    _ <- Console.printLine(s"structured output example") *> service.structuredOutputFormat()
  } yield ()

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    program
      .provide(
        OpenAIZioClientExample.live,
        OpenAIZioClient.layer,
        ZLayer.succeed(OpenAI.apply(OpenAIConfig("ollama", uri"http://localhost:11434/v1"))),
        HttpClientZioBackend.layer() >>> ZLayer.fromFunction((client: SttpClient) =>
          Slf4jLoggingBackend(client, LogConfig(logRequestBody = true, logResponseBody = true))
        )
      )
}
