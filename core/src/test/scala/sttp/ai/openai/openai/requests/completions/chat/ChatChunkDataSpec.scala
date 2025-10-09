package sttp.openai.requests.completions.chat

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.openai.fixtures
import sttp.openai.requests.completions.Stop.SingleStop
import sttp.openai.utils.ChatCompletionFixtures._
import sttp.openai.utils.JsonUtils

class ChatChunkDataSpec extends AnyFlatSpec with Matchers with EitherValues {

  "Given chat chunk completions response as Json" should "be properly deserialized to case class" in {
    import ChatChunkRequestResponseData.ChatChunkResponse._
    import ChatChunkRequestResponseData._

    // given
    val jsonResponse = fixtures.ChatChunkFixture.jsonResponse

    val choices = Seq(
      Choices(
        delta = Delta(
          content = Some("...")
        ),
        finishReason = None,
        index = 0
      ),
      Choices(
        delta = Delta(
          role = Some(Role.Assistant),
          content = Some("  Hi"),
          toolCalls = toolCalls
        ),
        finishReason = Some("stop"),
        index = 1
      )
    )

    val expectedResponse: ChatChunkResponse = ChatChunkResponse(
      id = "chatcmpl-76FxnKOjnPkDVYTAQ1wK8iUNFJPvR",
      `object` = "chat.completion",
      created = 1681725687,
      model = "gpt-3.5-turbo-0301",
      choices = choices,
      systemFingerprint = Some("systemFingerprint")
    )

    // when
    val givenResponse: Either[Exception, ChatChunkResponse] = JsonUtils.deserializeJsonSnake.apply(jsonResponse)

    // then
    givenResponse.value shouldBe expectedResponse
  }

  "Given completions request with streaming enabled as case class" should "be properly serialized to Json" in {
    import ChatRequestBody._
    import sttp.openai.requests.completions.chat.message._

    // given
    val givenRequest = ChatRequestBody.ChatBody(
      messages = messages,
      model = ChatCompletionModel.GPT35Turbo,
      frequencyPenalty = Some(0),
      maxTokens = Some(7),
      n = Some(1),
      presencePenalty = Some(0),
      temperature = Some(1),
      topP = Some(1),
      tools = Some(tools),
      responseFormat = Some(ResponseFormat.Text),
      toolChoice = Some(ToolChoice.ToolAuto),
      stop = Some(SingleStop("\n")),
      user = Some("testUser")
    )

    val jsonRequest = ujson.read(fixtures.ChatChunkFixture.jsonRequest)

    // when
    val serializedJson = ChatBody.withStreaming(givenRequest)

    // then
    serializedJson shouldBe jsonRequest
  }

  "Given chat chunk with usage data" should "properly deserialize usage field" in {
    import ChatChunkRequestResponseData._
    import sttp.openai.requests.completions.Usage

    // given
    val jsonWithUsage = """{
      |  "id": "chatcmpl-usage",
      |  "object": "chat.completion.chunk",
      |  "created": 1681725687,
      |  "model": "gpt-4",
      |  "system_fingerprint": "fp_123",
      |  "choices": [],
      |  "usage": {
      |    "prompt_tokens": 25,
      |    "completion_tokens": 12,
      |    "total_tokens": 37
      |  }
      |}""".stripMargin

    val expectedResponse = ChatChunkResponse(
      id = "chatcmpl-usage",
      `object` = "chat.completion.chunk",
      created = 1681725687,
      model = "gpt-4",
      choices = Seq.empty,
      systemFingerprint = Some("fp_123"),
      usage = Some(
        Usage(
          promptTokens = 25,
          completionTokens = 12,
          totalTokens = 37
        )
      )
    )

    // when
    val givenResponse: Either[Exception, ChatChunkResponse] = JsonUtils.deserializeJsonSnake[ChatChunkResponse].apply(jsonWithUsage)

    // then
    givenResponse.value shouldBe expectedResponse
  }
}
