package sttp.ai.claude.streaming.ox

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.{Ox, supervised}
import sttp.ai.claude.ClaudeClient
import sttp.ai.claude.ClaudeExceptions.ClaudeException
import sttp.ai.claude.ClaudeExceptions.ClaudeException.DeserializationClaudeException
import sttp.ai.claude.config.ClaudeConfig
import sttp.ai.claude.models.{ClaudeModel, Message}
import sttp.ai.claude.requests.MessageRequest
import sttp.ai.claude.responses.MessageStreamResponse
import sttp.ai.claude.responses.MessageStreamResponse.EventData.DoneEvent
import sttp.ai.core.json.SnakePickle.*
import sttp.client4.DefaultSyncBackend
import sttp.client4.testing.ResponseStub
import sttp.model.StatusCode
import sttp.model.StatusCode.*
import sttp.model.sse.ServerSentEvent

import java.io.{ByteArrayInputStream, InputStream}

class ClaudeOxClientSpec extends AnyFlatSpec with Matchers with EitherValues {

  private val errorMessage = "Some error message."

  private def errorResponse(message: String, errorType: String): String =
    s"""{
       |  "type": "error",
       |  "error": {
       |    "type": "$errorType",
       |    "message": "$message"
       |  }
       |}""".stripMargin

  private val testData: Seq[(StatusCode, String, Class[? <: ClaudeException])] = List(
    (TooManyRequests, "rate_limit_error", classOf[ClaudeException.RateLimitException]),
    (BadRequest, "invalid_request_error", classOf[ClaudeException.InvalidRequestException]),
    (NotFound, "invalid_request_error", classOf[ClaudeException.InvalidRequestException]),
    (Unauthorized, "authentication_error", classOf[ClaudeException.AuthenticationException]),
    (Forbidden, "permission_error", classOf[ClaudeException.PermissionException]),
    (Conflict, "api_error", classOf[ClaudeException.APIException]),
    (ServiceUnavailable, "api_error", classOf[ClaudeException.APIException]),
    (Gone, "api_error", classOf[ClaudeException.APIException])
  )

  for ((statusCode, claudeErrorType, expectedErrorClass) <- testData)
    s"Service response with status code: $statusCode" should s"return properly deserialized ${expectedErrorClass.getSimpleName}" in {
      // given
      val errorJson = errorResponse(errorMessage, claudeErrorType)
      val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespondAdjust(errorJson, statusCode)
      val client = ClaudeClient(ClaudeConfig("test-token"))

      val givenRequest = MessageRequest.simple(
        model = ClaudeModel.Claude3_5Sonnet.value,
        messages = List(Message.user("Hello")),
        maxTokens = 100
      )

      // when
      val caught = client
        .createStreamedMessage(givenRequest)
        .send(stub)
        .body
        .left
        .value

      // then
      caught.getClass shouldBe expectedErrorClass
    }

  "Creating messages with failed stream due to invalid deserialization" should "return properly deserialized error" in {
    // given
    val invalidJson = Some("invalid json")
    val invalidEvent = ServerSentEvent(invalidJson)

    val streamedResponse = new ByteArrayInputStream(invalidEvent.toString.getBytes)

    val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(streamedResponse))
    val client = ClaudeClient(ClaudeConfig("test-token"))

    val givenRequest = MessageRequest.simple(
      model = ClaudeModel.Claude3_5Sonnet.value,
      messages = List(Message.user("Hello")),
      maxTokens = 100
    )

    // when
    supervised {
      val response = client
        .createStreamedMessage(givenRequest)
        .send(stub)
        .body
        .value
        .runToList()

      // then
      response.head shouldBe a[Left[DeserializationClaudeException, Any]]
    }
  }

  "Creating messages with successful response" should "ignore empty events and return properly deserialized list of chunks" in {
    // given
    val messageChunks = Seq.fill(3)(MessageStreamFixture.contentBlockDelta)

    val eventsToProcess = messageChunks.map(data => ServerSentEvent(Some(data)))
    val emptyEvent = ServerSentEvent()
    val events = eventsToProcess :+ emptyEvent

    val delimiter = "\n\n"
    supervised {
      val streamedResponse = new ByteArrayInputStream(
        events
          .map(_.toString + delimiter)
          .flatMap(_.getBytes)
          .toArray
      )

      // when & then
      assertStreamedMessage(streamedResponse, messageChunks.map(read[MessageStreamResponse](_)))
    }
  }

  "Creating messages with successful response" should "filter out [DONE] events and return properly deserialized list of chunks" in {
    // given
    val messageChunks = Seq.fill(3)(MessageStreamFixture.contentBlockDelta)

    val eventsToProcess = messageChunks.map(data => ServerSentEvent(Some(data)))
    val doneEvent = ServerSentEvent(Some(DoneEvent))
    val events = eventsToProcess :+ doneEvent

    val delimiter = "\n\n"
    supervised {
      val streamedResponse = new ByteArrayInputStream(
        events
          .map(_.toString + delimiter)
          .flatMap(_.getBytes)
          .toArray
      )

      // when & then
      assertStreamedMessage(streamedResponse, messageChunks.map(read[MessageStreamResponse](_)))
    }
  }

  private def assertStreamedMessage(givenResponse: InputStream, expectedResponse: Seq[MessageStreamResponse])(using Ox) = {
    val stub = DefaultSyncBackend.stub.whenAnyRequest.thenRespond(ResponseStub.adjust(givenResponse))
    val client = ClaudeClient(ClaudeConfig("test-token"))

    val givenRequest = MessageRequest.simple(
      model = ClaudeModel.Claude3_5Sonnet.value,
      messages = List(Message.user("Hello")),
      maxTokens = 100
    )

    // when
    val response = client
      .createStreamedMessage(givenRequest)
      .send(stub)
      .body
      .value
      .runToList()
      .map(_.value)

    // then
    response shouldBe expectedResponse
  }
}

object MessageStreamFixture {
  // Note: type must be first for uPickle's tagged union deserialization
  val contentBlockDelta: String =
    """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}"""
}
