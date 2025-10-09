package sttp.openai.requests.threads.messages

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures
import sttp.openai.json.SnakePickle
import sttp.openai.requests.completions.chat.message.Attachment
import sttp.openai.requests.threads.messages.ThreadMessagesResponseData.Content.{TextContent, TextContentValue}
import sttp.openai.requests.threads.messages.ThreadMessagesResponseData.{DeleteMessageResponse, ListMessagesResponse, MessageData}
import sttp.openai.utils.JsonUtils

class ThreadMessagesDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given create thread message request as case class" should "be properly serialized to Json" in {

    // given
    val givenRequest = ThreadMessagesRequestBody.CreateMessage(
      role = "user",
      content = "How does AI work? Explain it in simple terms."
    )

    val jsonRequest: ujson.Value = ujson.read(fixtures.ThreadMessagesFixture.jsonCreateMessageRequest)

    // when
    val serializedJson: ujson.Value = SnakePickle.writeJs(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create thread message response as Json" should "be properly deserialized to case class" in {
    import sttp.openai.requests.threads.messages.ThreadMessagesResponseData.MessageData._

    // given
    val jsonResponse = fixtures.ThreadMessagesFixture.jsonCreateMessageResponse
    val expectedResponse: MessageData = MessageData(
      id = "msg_abc123",
      `object` = "thread.message",
      createdAt = 1699017614,
      threadId = Some("thread_abc123"),
      role = "user",
      content = Seq(
        TextContent(
          `type` = "text",
          text = TextContentValue(
            value = "How does AI work? Explain it in simple terms.",
            annotations = Seq.empty
          )
        )
      ),
      attachments = Some(Seq.empty),
      assistantId = None,
      runId = None,
      metadata = Map.empty
    )

    // when
    val givenResponse: Either[Exception, MessageData] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given list messages response as Json" should "be properly deserialized to case class" in {
    import ListMessagesResponse._
    // given
    val jsonResponse = fixtures.ThreadMessagesFixture.jsonListMessagesResponse
    val expectedResponse: ListMessagesResponse = ListMessagesResponse(
      `object` = "list",
      data = Seq(
        MessageData(
          id = "msg_abc123",
          `object` = "thread.message",
          createdAt = 1699016383,
          threadId = Some("thread_abc123"),
          role = "user",
          content = Seq(
            TextContent(
              `type` = "text",
              text = TextContentValue(
                value = "How does AI work? Explain it in simple terms.",
                annotations = Seq.empty
              )
            )
          ),
          attachments = Some(Seq.empty),
          assistantId = None,
          runId = None,
          metadata = Map.empty
        ),
        MessageData(
          id = "msg_abc456",
          `object` = "thread.message",
          createdAt = 1699016383,
          threadId = Some("thread_abc123"),
          role = "user",
          content = Seq(
            TextContent(
              `type` = "text",
              text = TextContentValue(
                value = "Hello, what is AI?",
                annotations = Seq.empty
              )
            )
          ),
          attachments = Some(Seq(Attachment(Some("file-abc123")))),
          assistantId = None,
          runId = None,
          metadata = Map.empty
        )
      ),
      firstId = "msg_abc123",
      lastId = "msg_abc456",
      hasMore = false
    )

    // when
    val givenResponse: Either[Exception, ListMessagesResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given retrieve message response as Json" should "be properly deserialized to case class" in {
    import MessageData._
    // given
    val jsonResponse = fixtures.ThreadMessagesFixture.jsonRetrieveMessageResponse
    val expectedResponse: MessageData = MessageData(
      id = "msg_abc123",
      `object` = "thread.message",
      createdAt = 1699017614,
      threadId = Some("thread_abc123"),
      role = "user",
      content = Seq(
        TextContent(
          `type` = "text",
          text = TextContentValue(
            value = "How does AI work? Explain it in simple terms.",
            annotations = Seq.empty
          )
        )
      ),
      attachments = None,
      assistantId = None,
      runId = None,
      metadata = Map.empty
    )

    // when
    val givenResponse: Either[Exception, MessageData] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given modify message response as Json" should "be properly deserialized to case class" in {
    import MessageData._
    // given
    val jsonResponse = fixtures.ThreadMessagesFixture.jsonModifyMessageResponse
    val expectedResponse: MessageData = MessageData(
      id = "msg_abc123",
      `object` = "thread.message",
      createdAt = 1699017614,
      threadId = Some("thread_abc123"),
      role = "user",
      content = Seq(
        TextContent(
          `type` = "text",
          text = TextContentValue(
            value = "How does AI work? Explain it in simple terms.",
            annotations = Seq.empty
          )
        )
      ),
      attachments = Some(Seq.empty),
      assistantId = None,
      runId = None,
      metadata = Map(
        "modified" -> "true",
        "user" -> "abc123"
      )
    )

    // when
    val givenResponse: Either[Exception, MessageData] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given delete message response as Json" should "be properly deserialized to case class" in {
    import DeleteMessageResponse._
    // given
    val jsonResponse = fixtures.ThreadMessagesFixture.jsonDeleteMessageResponse
    val expectedResponse: DeleteMessageResponse = DeleteMessageResponse(
      id = "msg_abc123",
      deleted = true
    )

    // when
    val givenResponse: Either[Exception, DeleteMessageResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }
}
