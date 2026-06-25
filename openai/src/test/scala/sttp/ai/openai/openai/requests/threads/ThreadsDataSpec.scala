package sttp.ai.openai.requests.threads

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.ai.openai.fixtures
import sttp.ai.openai.requests.assistants.Tool.{CodeInterpreter, FileSearch}
import sttp.ai.openai.requests.completions.chat.message.Attachment
import sttp.ai.openai.requests.threads.messages.ThreadMessagesRequestBody.CreateMessage
import io.circe.parser.{decode, parse}
import io.circe.syntax._
import sttp.ai.openai.json.OpenAIDerivedCodecs._
import sttp.ai.openai.json.OpenAIManualCodecs._
class ThreadsDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given empty create thread request as case class" should "be properly serialized to Json" in {

    // given
    val givenRequest = ThreadsRequestBody.CreateThreadBody()

    val jsonRequest: io.circe.Json = parse(fixtures.ThreadsFixture.jsonCreateEmptyThreadRequest).value

    // when
    val serializedJson: io.circe.Json = givenRequest.asJson.deepDropNullValues

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create threads request with messages and no attachments" should "be properly serialized to Json" in {

    // given
    val givenRequest = ThreadsRequestBody.CreateThreadBody(
      messages = Some(
        Seq(
          CreateMessage(
            role = "user",
            content = "Hello, what is AI?"
          ),
          CreateMessage(
            role = "user",
            content = "How does AI work? Explain it in simple terms."
          )
        )
      )
    )

    val jsonRequest: io.circe.Json = parse(fixtures.ThreadsFixture.jsonCreateThreadWithMessagesRequestNoAttachments).value

    // when
    val serializedJson: io.circe.Json = givenRequest.asJson.deepDropNullValues

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create threads request with messages" should "be properly serialized to Json" in {

    // given
    val givenRequest = ThreadsRequestBody.CreateThreadBody(
      messages = Some(
        Seq(
          CreateMessage(
            role = "user",
            content = "Hello, what is AI?",
            attachments = Some(Seq(Attachment(Some("file-abc123"), Some(Seq(CodeInterpreter, FileSearch)))))
          ),
          CreateMessage(
            role = "user",
            content = "How does AI work? Explain it in simple terms."
          )
        )
      )
    )

    val jsonRequest: io.circe.Json = parse(fixtures.ThreadsFixture.jsonCreateThreadWithMessagesRequest).value

    // when
    val serializedJson: io.circe.Json = givenRequest.asJson.deepDropNullValues

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create threads request with messages and metadata" should "be properly serialized to Json" in {

    // given
    val givenRequest = ThreadsRequestBody.CreateThreadBody(
      messages = Some(
        Seq(
          CreateMessage(
            role = "user",
            content = "Hello, what is AI?",
            attachments = Some(Seq(Attachment(Some("file-abc456"), Some(Seq(CodeInterpreter)))))
          ),
          CreateMessage(
            role = "user",
            content = "How does AI work? Explain it in simple terms."
          )
        )
      ),
      metadata = Some(Map("modified" -> "true", "user" -> "abc123"))
    )

    val jsonRequest: io.circe.Json = parse(fixtures.ThreadsFixture.jsonCreateThreadWithMessagesAndMetadataRequest).value

    // when
    val serializedJson: io.circe.Json = givenRequest.asJson.deepDropNullValues

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given create empty thread response as Json" should "be properly deserialized to case class" in {
    import sttp.ai.openai.requests.threads.ThreadsResponseData.ThreadData._
    import sttp.ai.openai.requests.threads.ThreadsResponseData._

    // given
    val jsonResponse = fixtures.ThreadsFixture.jsonCreateEmptyThreadResponse
    val expectedResponse: ThreadData = ThreadData(
      id = "thread_abc123",
      `object` = "thread",
      createdAt = Some(1699012949)
    )

    // when
    val givenResponse: Either[Exception, ThreadData] = decode[ThreadData](jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given create thread with messages and metadata response as Json" should "be properly deserialized to case class" in {
    import sttp.ai.openai.requests.threads.ThreadsResponseData.ThreadData._
    import sttp.ai.openai.requests.threads.ThreadsResponseData._

    // given
    val jsonResponse = fixtures.ThreadsFixture.jsonCreateThreadWithMessagesAndMetadataResponse
    val expectedResponse: ThreadData = ThreadData(
      id = "thread_abc123",
      `object` = "thread",
      createdAt = Some(1699014083),
      metadata = Map("modified" -> "true", "user" -> "abc123")
    )

    // when
    val givenResponse: Either[Exception, ThreadData] = decode[ThreadData](jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given delete thread response as Json" should "be properly deserialized to case class" in {
    import sttp.ai.openai.requests.threads.ThreadsResponseData.DeleteThreadResponse._
    import sttp.ai.openai.requests.threads.ThreadsResponseData._

    // given
    val jsonResponse = fixtures.ThreadsFixture.jsonDeleteThreadResponse
    val expectedResponse: DeleteThreadResponse = DeleteThreadResponse(
      id = "thread_abc123",
      `object` = "thread.deleted",
      deleted = true
    )

    // when
    val givenResponse: Either[Exception, DeleteThreadResponse] = decode[DeleteThreadResponse](jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }
}
